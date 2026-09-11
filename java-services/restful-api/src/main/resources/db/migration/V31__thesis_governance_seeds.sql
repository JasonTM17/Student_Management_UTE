-- V31__thesis_governance_seeds.sql
-- Phase P1a seeds: the faculty-head (TRUONG_KHOA) role for round governance and
-- a deterministic demo identity so a fresh stack reproduces the full workflow.
-- Style follows V28: idempotent, scoped to synthetic demo rows.

INSERT INTO campuscore_auth."Role" ("id", "name", "description", "isSystem")
SELECT 'role-truong-khoa', 'TRUONG_KHOA',
       'Faculty head: owns thesis rounds, councils and result publication', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."Role" WHERE "name" = 'TRUONG_KHOA'
);

-- The existing demo administrator also carries the faculty-head role so the
-- seeded demo walkthrough keeps working with a single login.
INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-admin-truongkhoa', 'admin-user', 'role-truong-khoa'
WHERE EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'admin-user')
  AND NOT EXISTS (
      SELECT 1 FROM campuscore_auth."UserRole"
      WHERE "userId" = 'admin-user' AND "roleId" = 'role-truong-khoa'
  );

INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "status", "emailVerified")
SELECT 'truongkhoa-user-001', 'truongkhoa@campuscore.demo', "password",
       'Demo', 'Trưởng khoa CNTT', 'ACTIVE', TRUE
FROM campuscore_auth."User" WHERE "id" = 'admin-user'
ON CONFLICT ("id") DO NOTHING;

INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-truongkhoa-001', 'truongkhoa-user-001', 'role-truong-khoa'
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."UserRole"
    WHERE "userId" = 'truongkhoa-user-001'
);
