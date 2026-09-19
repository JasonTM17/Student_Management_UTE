-- Approved groups are academic records: this execution contract requires
-- 3..4 members and exactly one leader. Reports/files are never deleted.
-- Terminal anomalies stop the migration for an explicit governance decision.
-- Flyway must run this migration with thesis writes fenced.  The table lock
-- below is intentionally incompatible with concurrent INSERT/UPDATE/DELETE;
-- application rollback is forward-only after this migration.
LOCK TABLE thesis.thesis_group, thesis.thesis_group_member IN SHARE ROW EXCLUSIVE MODE;

DO $$
DECLARE
    terminal_invalid_count INTEGER;
    structural_invalid_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO terminal_invalid_count
    FROM (
        SELECT g.id
        FROM thesis.thesis_group g
        JOIN thesis.thesis_registration_round r ON r.id = g.round_id
        LEFT JOIN thesis.thesis_group_member gm ON gm.group_id = g.id
        WHERE g.approval_status = 'APPROVED'
          AND r.status IN ('RESULTS_PUBLISHED', 'CLOSED', 'CANCELLED')
        GROUP BY g.id
        HAVING COUNT(gm.id) NOT BETWEEN 1 AND 4
    ) invalid_terminal_groups;

    IF terminal_invalid_count > 0 THEN
        RAISE EXCEPTION
            'V65 stopped: % approved terminal-round groups have an invalid member count',
            terminal_invalid_count;
    END IF;

    -- Unique indexes and deferred triggers cannot repair historical corruption
    -- that is not touched by the migration.  Census every group before adding
    -- the new oracle and stop with an actionable count rather than silently
    -- rewriting academic membership/leader data.
    SELECT COUNT(*) INTO structural_invalid_count
    FROM (
        SELECT g.id
        FROM thesis.thesis_group g
        LEFT JOIN thesis.thesis_group_member gm ON gm.group_id = g.id
        GROUP BY g.id, g.round_id, g.leader_student_id
        HAVING (
                   COUNT(gm.id) > 0
                   AND COUNT(*) FILTER (WHERE gm.is_leader) <> 1
               )
            OR (
                   COUNT(gm.id) > 0
                   AND NOT EXISTS (
                       SELECT 1
                       FROM thesis.thesis_group_member matching_leader
                       WHERE matching_leader.group_id = g.id
                         AND matching_leader.is_leader = TRUE
                         AND matching_leader.student_id = g.leader_student_id
                   )
               )
            OR EXISTS (
                   SELECT 1
                   FROM thesis.thesis_group_member cross_round
                   WHERE cross_round.group_id = g.id
                     AND cross_round.round_id <> g.round_id
               )
            OR COUNT(gm.member_order) <> COUNT(DISTINCT gm.member_order)
            OR COUNT(gm.id) FILTER (WHERE gm.member_order NOT BETWEEN 1 AND 4) > 0
    ) invalid_structural_groups;

    IF structural_invalid_count > 0 THEN
        RAISE EXCEPTION
            'V65 stopped: % thesis groups violate leader, round, or member-order invariants',
            structural_invalid_count;
    END IF;
END $$;

-- Re-open only live approved groups that cannot satisfy the new contract. The
-- report row/object is deliberately retained for audit and later resubmission.
UPDATE thesis.thesis_group g
SET approval_status = 'PENDING',
    approved_by = NULL,
    approved_at = NULL,
    rejection_reason = 'Group requires 3-4 members before it can be re-approved',
    updated_at = CURRENT_TIMESTAMP,
    version = g.version + 1
FROM thesis.thesis_registration_round r
WHERE r.id = g.round_id
  AND r.status NOT IN ('RESULTS_PUBLISHED', 'CLOSED', 'CANCELLED')
  AND g.approval_status = 'APPROVED'
  AND (
      SELECT COUNT(*) FROM thesis.thesis_group_member gm WHERE gm.group_id = g.id
  ) NOT BETWEEN 3 AND 4;

-- V1 allowed only orders 1..3. Replace that bound atomically for the new
-- contract and backstop direct writes with order uniqueness and one leader.
ALTER TABLE thesis.thesis_group_member
    DROP CONSTRAINT IF EXISTS thesis_group_member_order_valid;
ALTER TABLE thesis.thesis_group_member
    ADD CONSTRAINT thesis_group_member_order_valid CHECK (member_order BETWEEN 1 AND 4);

CREATE UNIQUE INDEX IF NOT EXISTS thesis_group_member_order_unique
    ON thesis.thesis_group_member (group_id, member_order);
CREATE UNIQUE INDEX IF NOT EXISTS thesis_group_member_one_leader_unique
    ON thesis.thesis_group_member (group_id)
    WHERE is_leader = TRUE;

CREATE OR REPLACE FUNCTION thesis.validate_group_membership_state(p_group_id UUID)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    group_round_id UUID;
    group_leader_id VARCHAR(120);
    member_count INTEGER;
    leader_count INTEGER;
    current_approval_status VARCHAR(32);
BEGIN
    -- Serialize all membership changes for a group at the database boundary.
    -- The lock is acquired by the deferred trigger at COMMIT, so concurrent
    -- deletes/moves cannot both validate against a stale member snapshot.
    PERFORM 1 FROM thesis.thesis_group WHERE id = p_group_id FOR UPDATE;

    SELECT g.round_id, g.leader_student_id, g.approval_status
      INTO group_round_id, group_leader_id, current_approval_status
      FROM thesis.thesis_group g
     WHERE g.id = p_group_id;
    IF NOT FOUND THEN
        RETURN;
    END IF;

    SELECT COUNT(*)::INTEGER,
           COUNT(*) FILTER (WHERE gm.is_leader)::INTEGER
      INTO member_count, leader_count
      FROM thesis.thesis_group_member gm
     WHERE gm.group_id = p_group_id;

    IF member_count > 4 THEN
        RAISE EXCEPTION 'Thesis group % cannot have more than 4 members', p_group_id
            USING ERRCODE = '23514', CONSTRAINT = 'thesis_group_member_count';
    END IF;
    IF member_count > 0 AND leader_count <> 1 THEN
        RAISE EXCEPTION 'Thesis group % must have exactly one leader', p_group_id
            USING ERRCODE = '23514', CONSTRAINT = 'thesis_group_one_leader';
    END IF;
    IF member_count > 0 AND NOT EXISTS (
        SELECT 1 FROM thesis.thesis_group_member gm
        WHERE gm.group_id = p_group_id
          AND gm.is_leader = TRUE
          AND gm.student_id = group_leader_id
    ) THEN
        RAISE EXCEPTION 'Thesis group % leader_student_id must match its leader member', p_group_id
            USING ERRCODE = '23514', CONSTRAINT = 'thesis_group_leader_consistency';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM thesis.thesis_group_member gm
        WHERE gm.group_id = p_group_id
          AND gm.round_id <> group_round_id
    ) THEN
        RAISE EXCEPTION 'Thesis group % has a member from another round', p_group_id
            USING ERRCODE = '23514', CONSTRAINT = 'thesis_group_round_consistency';
    END IF;
    IF current_approval_status = 'APPROVED' AND member_count NOT BETWEEN 3 AND 4 THEN
        RAISE EXCEPTION
            'Approved thesis group % must have between 3 and 4 members (found %)',
            p_group_id, member_count
            USING ERRCODE = '23514', CONSTRAINT = 'thesis_approved_group_member_count';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION thesis.enforce_group_membership_state()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'thesis_group_member' THEN
        IF TG_OP = 'UPDATE' AND OLD.group_id IS DISTINCT FROM NEW.group_id THEN
            -- Always acquire moved-group locks in a stable order to avoid a
            -- delete/move deadlock when two transactions swap membership.
            IF OLD.group_id::text < NEW.group_id::text THEN
                PERFORM thesis.validate_group_membership_state(OLD.group_id);
                PERFORM thesis.validate_group_membership_state(NEW.group_id);
            ELSE
                PERFORM thesis.validate_group_membership_state(NEW.group_id);
                PERFORM thesis.validate_group_membership_state(OLD.group_id);
            END IF;
        ELSE
            IF TG_OP <> 'INSERT' THEN
                PERFORM thesis.validate_group_membership_state(OLD.group_id);
            END IF;
            IF TG_OP <> 'DELETE' THEN
                PERFORM thesis.validate_group_membership_state(NEW.group_id);
            END IF;
        END IF;
    ELSE
        PERFORM thesis.validate_group_membership_state(NEW.id);
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS thesis_group_membership_state_group ON thesis.thesis_group;
CREATE CONSTRAINT TRIGGER thesis_group_membership_state_group
AFTER INSERT OR UPDATE OF round_id, leader_student_id, approval_status ON thesis.thesis_group
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION thesis.enforce_group_membership_state();

DROP TRIGGER IF EXISTS thesis_group_membership_state_member ON thesis.thesis_group_member;
CREATE CONSTRAINT TRIGGER thesis_group_membership_state_member
AFTER INSERT OR UPDATE OF group_id, round_id, student_id, is_leader OR DELETE
ON thesis.thesis_group_member
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION thesis.enforce_group_membership_state();
