-- Publication guard and governance visibility additions.
--
-- results_published_at       a durable publication instant; updated_at moves
--                            with every unrelated write, so the retract grace
--                            window needs its own column. Set by publishResults
--                            and cleared by the retract escape hatch.
--
-- size_exception_*           a runtime, reason-bearing small-group exception
--                            for the admin roster override. The V113
--                            legacy_group_size_exception column cannot serve
--                            here: its trigger makes the marker write-once
--                            (migration-only) and freezes the whole group, so
--                            a runtime override needs its own columns. The
--                            membership validator below accepts the new flag
--                            for a 1-2 member approved group only.
--
-- source_key                 a producer tag on inbox rows so lifecycle
--                            notices can be recalled exactly (the results-
--                            published fan-out uses it to undo its notices on
--                            a retract) without touching unrelated rows.
--
-- notification pair unique   two announcement fan-outs racing on the same
--                            (announcement_id, user_id) pair could both insert;
--                            the unique index plus ON CONFLICT DO NOTHING makes
--                            delivery idempotent. Existing duplicates are
--                            removed first keeping the oldest row per pair.
--                            Rows with a NULL announcement_id (thesis and
--                            legacy notices) are unaffected: NULL keys never
--                            conflict in a Postgres unique index.

ALTER TABLE thesis.thesis_registration_round
    ADD COLUMN IF NOT EXISTS results_published_at TIMESTAMPTZ;

ALTER TABLE thesis.thesis_group
    ADD COLUMN IF NOT EXISTS size_exception_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS size_exception_by VARCHAR(120),
    ADD COLUMN IF NOT EXISTS size_exception_at TIMESTAMPTZ;

ALTER TABLE notifications.notification
    ADD COLUMN IF NOT EXISTS source_key VARCHAR(160);

CREATE INDEX IF NOT EXISTS notification_source_key_idx
    ON notifications.notification (source_key)
    WHERE source_key IS NOT NULL;

DELETE FROM notifications.notification n
WHERE n.announcement_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM notifications.notification older
      WHERE older.announcement_id = n.announcement_id
        AND older.user_id = n.user_id
        AND (older.created_at, older.id) < (n.created_at, n.id));

CREATE UNIQUE INDEX IF NOT EXISTS notification_announcement_user_uniq
    ON notifications.notification (announcement_id, user_id);

-- The validator keeps every V113 rule and additionally honours the runtime
-- size exception: an approved group may hold 1-2 members only while it carries
-- a documented governance reason (size_exception_reason). The legacy marker
-- path is unchanged.
CREATE OR REPLACE FUNCTION thesis.validate_group_membership_state(p_group_id UUID)
RETURNS VOID LANGUAGE plpgsql SET search_path = pg_catalog AS $$
DECLARE
    group_round_id UUID; group_leader_id VARCHAR(120); member_count INTEGER;
    leader_count INTEGER; current_approval_status VARCHAR(32); group_status VARCHAR(32);
    round_status VARCHAR(32); legacy_exception BOOLEAN; size_exception BOOLEAN;
BEGIN
    PERFORM 1 FROM thesis.thesis_group WHERE id = p_group_id FOR UPDATE;
    SELECT g.round_id, g.leader_student_id, g.approval_status, g.status,
           g.legacy_group_size_exception, g.size_exception_reason IS NOT NULL, r.status
    INTO group_round_id, group_leader_id, current_approval_status, group_status,
         legacy_exception, size_exception, round_status
    FROM thesis.thesis_group g JOIN thesis.thesis_registration_round r ON r.id = g.round_id
    WHERE g.id = p_group_id;
    IF NOT FOUND THEN RETURN; END IF;
    SELECT COUNT(*)::INTEGER, COUNT(*) FILTER (WHERE gm.is_leader)::INTEGER
    INTO member_count, leader_count FROM thesis.thesis_group_member gm WHERE gm.group_id = p_group_id;
    IF member_count > 5 THEN
        RAISE EXCEPTION 'Thesis group % cannot have more than 5 members', p_group_id
            USING ERRCODE = '23514', CONSTRAINT = 'thesis_group_member_count';
    END IF;
    IF member_count > 0 AND leader_count <> 1 THEN
        RAISE EXCEPTION 'Thesis group % must have exactly one leader', p_group_id
            USING ERRCODE = '23514', CONSTRAINT = 'thesis_group_one_leader';
    END IF;
    IF member_count > 0 AND NOT EXISTS (SELECT 1 FROM thesis.thesis_group_member gm
        WHERE gm.group_id = p_group_id AND gm.is_leader AND gm.student_id = group_leader_id) THEN
        RAISE EXCEPTION 'Thesis group % leader_student_id must match its leader member', p_group_id
            USING ERRCODE = '23514', CONSTRAINT = 'thesis_group_leader_consistency';
    END IF;
    IF EXISTS (SELECT 1 FROM thesis.thesis_group_member gm
        WHERE gm.group_id = p_group_id AND gm.round_id <> group_round_id) THEN
        RAISE EXCEPTION 'Thesis group % has a member from another round', p_group_id
            USING ERRCODE = '23514', CONSTRAINT = 'thesis_group_round_consistency';
    END IF;
    IF legacy_exception AND (current_approval_status <> 'APPROVED'
        OR member_count NOT BETWEEN 1 AND 2
        OR (group_status <> 'CANCELLED' AND round_status NOT IN ('RESULTS_PUBLISHED', 'CLOSED', 'CANCELLED'))) THEN
        RAISE EXCEPTION 'Historical thesis group % no longer matches its frozen exception', p_group_id
            USING ERRCODE = '23514', CONSTRAINT = 'thesis_legacy_size_exception_invalid';
    END IF;
    IF size_exception AND (current_approval_status <> 'APPROVED' OR member_count NOT BETWEEN 1 AND 5) THEN
        RAISE EXCEPTION 'Thesis group % size exception only covers an approved roster', p_group_id
            USING ERRCODE = '23514', CONSTRAINT = 'thesis_size_exception_invalid';
    END IF;
    IF current_approval_status = 'APPROVED' AND member_count NOT BETWEEN 3 AND 5
        AND NOT legacy_exception AND NOT size_exception THEN
        RAISE EXCEPTION 'Approved thesis group % must have between 3 and 5 members (found %)', p_group_id, member_count
            USING ERRCODE = '23514', CONSTRAINT = 'thesis_approved_group_member_count';
    END IF;
END;
$$;

-- The retract-publication escape moves a published round back to
-- REGISTRATION_CLOSED. V113's round guard treats any terminal -> live move as
-- a forbidden reopen, which would also block a governed retraction on a round
-- that happens to contain a frozen historical small group. The one transition
-- publication itself needs (RESULTS_PUBLISHED -> REGISTRATION_CLOSED) is
-- permitted; every other reopen stays blocked.
CREATE OR REPLACE FUNCTION thesis.guard_legacy_group_size_round()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
    IF OLD.status IN ('RESULTS_PUBLISHED', 'CLOSED', 'CANCELLED')
       AND NEW.status NOT IN ('RESULTS_PUBLISHED', 'CLOSED', 'CANCELLED')
       AND NOT (OLD.status = 'RESULTS_PUBLISHED' AND NEW.status = 'REGISTRATION_CLOSED')
       AND EXISTS (SELECT 1 FROM thesis.thesis_group g
                   WHERE g.round_id = OLD.id AND g.legacy_group_size_exception) THEN
        RAISE EXCEPTION 'A round containing frozen historical small groups cannot be reopened'
            USING ERRCODE = '23514', CONSTRAINT = 'thesis_legacy_size_round_read_only';
    END IF;
    RETURN NEW;
END;
$$;
