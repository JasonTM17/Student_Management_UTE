-- Align the thesis module with the faculty brief:
--   * a group holds at most three members including the leader
--     (supersedes the V65/V72 3-4 ruling, which the updated brief overrides);
--   * every thesis topic may carry one assigned GVPB (counter-reviewer)
--     lecturer who scores it independently of the defense council;
--   * each submitted score may carry a written evaluation comment that is
--     shown to students only after results are published.

-- Serialize with concurrent roster mutations while the cleanup rewrites
-- member_order (V65/V94 precedent).
LOCK TABLE thesis.thesis_group, thesis.thesis_group_member IN SHARE ROW EXCLUSIVE MODE;

-- Replace the validator first so the deferred triggers check the new bounds
-- when this migration commits. The function body is identical to V65 except
-- for the member-count bounds. SET search_path re-pins what
-- 20260927040717_pin_thesis_function_search_paths.sql applied — CREATE OR
-- REPLACE resets proconfig, so the pin must live in the function definition.
CREATE OR REPLACE FUNCTION thesis.validate_group_membership_state(p_group_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $$
DECLARE
    group_round_id UUID;
    group_leader_id VARCHAR(120);
    member_count INTEGER;
    leader_count INTEGER;
    current_approval_status VARCHAR(32);
BEGIN
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

    IF member_count > 3 THEN
        RAISE EXCEPTION 'Thesis group % cannot have more than 3 members', p_group_id
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
    IF current_approval_status = 'APPROVED' AND member_count NOT BETWEEN 1 AND 3 THEN
        RAISE EXCEPTION
            'Approved thesis group % must have between 1 and 3 members (found %)',
            p_group_id, member_count
            USING ERRCODE = '23514', CONSTRAINT = 'thesis_approved_group_member_count';
    END IF;
END;
$$;

-- Seats beyond the spec maximum can never be legal again on live rounds.
-- Terminal-round rosters (RESULTS_PUBLISHED/CLOSED/CANCELLED) are academic
-- history and keep their member rows untouched; the CHECK below is added NOT
-- VALID so that history cannot block the constraint while every new write is
-- still enforced. The V94 active-participation trigger forbids deleting
-- members of CANCELLED groups, so it is suspended for this one controlled
-- cleanup and re-enabled for every later mutation.
ALTER TABLE thesis.thesis_group_member
    DISABLE TRIGGER thesis_group_member_active_participation;

-- Re-open live approved groups BEFORE removing any seat: a roster that loses a
-- member must be re-approved rather than silently staying APPROVED, and any
-- group already outside the new contract goes back to PENDING too. Cancelled
-- groups are already dissolved and must not be flipped back to PENDING.
UPDATE thesis.thesis_group g
SET approval_status = 'PENDING',
    approved_by = NULL,
    approved_at = NULL,
    rejection_reason = 'Group must satisfy the 1-3 member rule before it can be re-approved',
    updated_at = CURRENT_TIMESTAMP,
    version = g.version + 1
FROM thesis.thesis_registration_round r
WHERE r.id = g.round_id
  AND r.status NOT IN ('RESULTS_PUBLISHED', 'CLOSED', 'CANCELLED')
  AND g.status IS DISTINCT FROM 'CANCELLED'
  AND g.approval_status = 'APPROVED'
  AND (
      EXISTS (
          SELECT 1 FROM thesis.thesis_group_member gm
          WHERE gm.group_id = g.id AND gm.member_order > 3
      )
      OR (
          SELECT COUNT(*) FROM thesis.thesis_group_member gm WHERE gm.group_id = g.id
      ) NOT BETWEEN 1 AND 3
  );

-- Live groups only: keep the leader seat plus the lowest-order members up to
-- the three-seat limit. A member row is never removed when it is the group's
-- leader — by flag or by leader_student_id — so a surviving roster always
-- satisfies the one-leader invariant at commit.
WITH affected AS (
    SELECT g.id, g.leader_student_id
    FROM thesis.thesis_group g
    JOIN thesis.thesis_registration_round r ON r.id = g.round_id
    WHERE r.status NOT IN ('RESULTS_PUBLISHED', 'CLOSED', 'CANCELLED')
      AND g.status IS DISTINCT FROM 'CANCELLED'
      AND EXISTS (
          SELECT 1 FROM thesis.thesis_group_member gm
          WHERE gm.group_id = g.id AND gm.member_order > 3)
),
ranked AS (
    SELECT gm.id,
           ROW_NUMBER() OVER (
               PARTITION BY gm.group_id
               ORDER BY (gm.is_leader OR gm.student_id = a.leader_student_id) DESC,
                        gm.member_order
           ) AS keep_rank
    FROM thesis.thesis_group_member gm
    JOIN affected a ON a.id = gm.group_id
)
DELETE FROM thesis.thesis_group_member gm
USING ranked rn
WHERE rn.id = gm.id AND rn.keep_rank > 3;

-- Compact the surviving seats of every affected live group into orders 1..n,
-- preserving their display order (member_order is a per-group seat label, not
-- globally unique).
WITH affected AS (
    SELECT g.id
    FROM thesis.thesis_group g
    JOIN thesis.thesis_registration_round r ON r.id = g.round_id
    WHERE r.status NOT IN ('RESULTS_PUBLISHED', 'CLOSED', 'CANCELLED')
      AND g.status IS DISTINCT FROM 'CANCELLED'
),
renumbered AS (
    SELECT gm.id,
           ROW_NUMBER() OVER (PARTITION BY gm.group_id ORDER BY gm.member_order) AS new_order
    FROM thesis.thesis_group_member gm
    JOIN affected a ON a.id = gm.group_id
)
UPDATE thesis.thesis_group_member gm
SET member_order = rn.new_order
FROM renumbered rn
WHERE rn.id = gm.id AND gm.member_order IS DISTINCT FROM rn.new_order;

ALTER TABLE thesis.thesis_group_member
    ENABLE TRIGGER thesis_group_member_active_participation;

ALTER TABLE thesis.thesis_group_member
    DROP CONSTRAINT IF EXISTS thesis_group_member_order_valid;
-- NOT VALID on purpose: historical terminal-round seats above 3 (if any) stay
-- readable, while every INSERT/UPDATE is checked immediately.
ALTER TABLE thesis.thesis_group_member
    ADD CONSTRAINT thesis_group_member_order_valid CHECK (member_order BETWEEN 1 AND 3) NOT VALID;

-- GVPB counter-reviewer: one assigned lecturer per topic, scoring the GVPB
-- component outside the council (so council_id becomes nullable) and before
-- the round's gvpb_deadline, enforced by ThesisCouncilService.
ALTER TABLE thesis.thesis_topic
    ADD COLUMN IF NOT EXISTS gvpb_lecturer_id VARCHAR(120);
CREATE INDEX IF NOT EXISTS thesis_topic_gvpb_idx
    ON thesis.thesis_topic (gvpb_lecturer_id);

ALTER TABLE thesis.thesis_topic_score
    ALTER COLUMN council_id DROP NOT NULL;
ALTER TABLE thesis.thesis_topic_score
    ADD COLUMN IF NOT EXISTS comment VARCHAR(1000);
