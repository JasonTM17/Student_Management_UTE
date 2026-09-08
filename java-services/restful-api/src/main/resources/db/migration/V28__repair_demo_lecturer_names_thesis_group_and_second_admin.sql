-- Purpose: repairs found by the 2026-09-07 twelve-role dogfood audit
-- (plans/260907-1700-campuscore-dogfood-audit-and-repair). Every statement is
-- idempotent and scoped to synthetic demo rows.

-- 1) V26 swapped its VALUES aliases for lecturers, writing the faculty name
--    ("Khoa CNTT") into "firstName" and the full name into "lastName" for
--    lecturer-user-002..012. Split the full name into family + given names;
--    both right-hand sides read the pre-update row, so a re-run is a no-op.
UPDATE campuscore_auth."User"
SET "lastName" = btrim(substring("lastName" FROM 1 FOR position(' ' IN "lastName") - 1)),
    "firstName" = btrim(substring("lastName" FROM position(' ' IN "lastName") + 1))
WHERE "email" LIKE 'lecturer%@campuscore.demo'
  AND "firstName" = 'Khoa CNTT'
  AND position(' ' IN "lastName") > 1;

-- 2) The seeded demo thesis group kept status DRAFT while holding a topic.
--    Every code path that assigns a topic moves the group to SUBMITTED, so
--    the supervisor approval demo was unreachable (approve returned
--    GROUP_APPROVAL_STATE_CONFLICT and the UI hid its actions).
UPDATE thesis.thesis_group
SET status = 'SUBMITTED'
WHERE id = '22222222-2222-2222-2222-222222222301'
  AND topic_id IS NOT NULL
  AND status = 'DRAFT';

-- 3) The four-eyes knowledge governance demo requires a second admin. Provide
--    a deterministic, idempotent demo identity instead of a hand-inserted row
--    so a fresh `docker compose up` reproduces the full demo.
INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "status", "emailVerified")
SELECT 'admin-user-002', 'admin002@campuscore.demo', "password", 'Demo', 'Admin Hai', 'ACTIVE', TRUE
FROM campuscore_auth."User" WHERE "id" = 'admin-user'
ON CONFLICT ("id") DO NOTHING;

INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-admin-002', 'admin-user-002', 'role-admin'
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."UserRole" WHERE "userId" = 'admin-user-002'
);
