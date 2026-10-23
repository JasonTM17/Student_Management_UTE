-- A seeded demo group that already carries a report row must not sit at
-- PENDING: the progress timeline derives a missing GROUP_APPROVED milestone
-- before a present REPORT_SUBMITTED one and reports the group as inconsistent
-- to its members. V65 reopened under-sized rosters while deliberately
-- retaining their report rows, and V112 re-pins the canonical demo group to
-- SUBMITTED + PENDING on environments where it was already approved, so both
-- fresh deployments and long-lived databases can strand a retained report
-- behind a missing approval. This restores the seeded approval the same way
-- the admin approval flow records it.
--
-- The guard is deliberately narrow so real user data is never touched: only
-- the three seeded group ids below qualify, the leader must be a demo
-- profile, a live report row must exist (databases without one stay
-- unchanged), and the roster must still satisfy every condition
-- thesis.validate_group_membership_state enforces for approved groups.
UPDATE thesis.thesis_group g
SET approval_status = 'APPROVED',
    approved_by = COALESCE(g.approved_by, 'truongkhoa-user-001'),
    approved_at = COALESCE(g.approved_at, CURRENT_TIMESTAMP),
    rejection_reason = NULL,
    updated_at = CURRENT_TIMESTAMP,
    version = g.version + 1
WHERE g.id IN (
        'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93',
        '165fa3b8-46cd-4225-aabe-a0e22315e5cd',
        '250b2c11-ff07-4b1b-ac54-cb6ac3392903')
  AND g.status IN ('ASSIGNED', 'SUBMITTED')
  AND g.approval_status = 'PENDING'
  AND g.legacy_group_size_exception = FALSE
  AND g.leader_student_id LIKE 'student-profile%'
  AND EXISTS (SELECT 1 FROM thesis.thesis_group_report rep
              WHERE rep.group_id = g.id)
  AND (SELECT COUNT(*) FROM thesis.thesis_group_member gm
       WHERE gm.group_id = g.id) BETWEEN 3 AND 5
  AND (SELECT COUNT(*) FROM thesis.thesis_group_member gm
       WHERE gm.group_id = g.id AND gm.is_leader) = 1
  AND EXISTS (SELECT 1 FROM thesis.thesis_group_member gm
              WHERE gm.group_id = g.id AND gm.is_leader
                AND gm.student_id = g.leader_student_id)
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_group_member gm
                  WHERE gm.group_id = g.id AND gm.round_id <> g.round_id);
