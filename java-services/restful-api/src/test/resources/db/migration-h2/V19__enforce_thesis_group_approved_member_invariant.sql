-- H2 twin of PostgreSQL V63. H2 cannot express a PostgreSQL deferred
-- constraint trigger, so service-level row locking tests cover the timing
-- invariant while these indexes/checks cover the portable row shape.
ALTER TABLE thesis.thesis_group_member
    ADD CONSTRAINT IF NOT EXISTS thesis_group_member_order_valid
    CHECK (member_order BETWEEN 1 AND 4);

CREATE UNIQUE INDEX IF NOT EXISTS thesis_group_member_order_unique
    ON thesis.thesis_group_member (group_id, member_order);

UPDATE thesis.thesis_group g
SET approval_status = 'PENDING',
    approved_by = NULL,
    approved_at = NULL,
    rejection_reason = 'Group requires 3-4 members before it can be re-approved',
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE g.approval_status = 'APPROVED'
  AND (SELECT COUNT(*) FROM thesis.thesis_group_member gm WHERE gm.group_id = g.id) NOT BETWEEN 3 AND 4;
