-- H2 twin of PostgreSQL V100: thesis groups hold at most three members
-- (leader included), topics carry one optional GVPB counter-reviewer, and
-- scores may carry an evaluation comment.
-- Mirrors V100 semantics: only live (non-terminal, non-CANCELLED) groups lose
-- seats; the leader row is never deleted; survivors compact into orders 1..n.
-- H2 carries no triggers/LOCK/NOT-VALID support, so those PG-only clauses have
-- no twin here.

-- Re-open approved groups before removing seats.
UPDATE thesis.thesis_group g
SET approval_status = 'PENDING',
    approved_by = NULL,
    approved_at = NULL,
    rejection_reason = 'Group must satisfy the 1-3 member rule before it can be re-approved',
    updated_at = CURRENT_TIMESTAMP,
    version = g.version + 1
WHERE g.approval_status = 'APPROVED'
  AND g.status <> 'CANCELLED'
  AND EXISTS (
      SELECT 1 FROM thesis.thesis_registration_round r
      WHERE r.id = g.round_id
        AND r.status NOT IN ('RESULTS_PUBLISHED', 'CLOSED', 'CANCELLED'))
  AND (
      EXISTS (
          SELECT 1 FROM thesis.thesis_group_member gm
          WHERE gm.group_id = g.id AND gm.member_order > 3
      )
      OR (
          SELECT COUNT(*) FROM thesis.thesis_group_member gm WHERE gm.group_id = g.id
      ) NOT BETWEEN 1 AND 3
  );

-- Keep the leader seat plus the lowest-order members up to three; delete the
-- rest of the excess seats in live groups.
DELETE FROM thesis.thesis_group_member gm
WHERE EXISTS (
      SELECT 1
      FROM thesis.thesis_group g
      JOIN thesis.thesis_registration_round r ON r.id = g.round_id
      WHERE g.id = gm.group_id
        AND r.status NOT IN ('RESULTS_PUBLISHED', 'CLOSED', 'CANCELLED')
        AND g.status <> 'CANCELLED')
  AND NOT gm.is_leader
  AND NOT EXISTS (
      SELECT 1 FROM thesis.thesis_group g2
      WHERE g2.id = gm.group_id AND g2.leader_student_id = gm.student_id)
  AND gm.member_order > 3;

-- A live group whose leader held seat 4 still has four members after the first
-- pass; drop the highest-order non-leader seat to reach the cap.
DELETE FROM thesis.thesis_group_member gm
WHERE NOT gm.is_leader
  AND EXISTS (
      SELECT 1
      FROM thesis.thesis_group g
      JOIN thesis.thesis_registration_round r ON r.id = g.round_id
      WHERE g.id = gm.group_id
        AND r.status NOT IN ('RESULTS_PUBLISHED', 'CLOSED', 'CANCELLED')
        AND g.status <> 'CANCELLED'
        AND g.leader_student_id <> gm.student_id
        AND (SELECT COUNT(*) FROM thesis.thesis_group_member m WHERE m.group_id = g.id) > 3
        AND gm.member_order = (SELECT MAX(m2.member_order) FROM thesis.thesis_group_member m2 WHERE m2.group_id = g.id));

-- Compact surviving seats into orders 1..n preserving display order.
UPDATE thesis.thesis_group_member gm
SET member_order = (
    SELECT COUNT(*) FROM thesis.thesis_group_member m2
    WHERE m2.group_id = gm.group_id AND m2.member_order <= gm.member_order)
WHERE EXISTS (
      SELECT 1 FROM thesis.thesis_group g
      JOIN thesis.thesis_registration_round r ON r.id = g.round_id
      WHERE g.id = gm.group_id
        AND r.status NOT IN ('RESULTS_PUBLISHED', 'CLOSED', 'CANCELLED')
        AND g.status <> 'CANCELLED');

ALTER TABLE thesis.thesis_group_member
    DROP CONSTRAINT IF EXISTS thesis_group_member_order_valid;
ALTER TABLE thesis.thesis_group_member
    ADD CONSTRAINT thesis_group_member_order_valid CHECK (member_order BETWEEN 1 AND 3);

ALTER TABLE thesis.thesis_topic
    ADD COLUMN IF NOT EXISTS gvpb_lecturer_id VARCHAR(120);
CREATE INDEX IF NOT EXISTS thesis_topic_gvpb_idx
    ON thesis.thesis_topic (gvpb_lecturer_id);

ALTER TABLE thesis.thesis_topic_score
    ALTER COLUMN council_id DROP NOT NULL;
ALTER TABLE thesis.thesis_topic_score
    ADD COLUMN IF NOT EXISTS comment VARCHAR(1000);
