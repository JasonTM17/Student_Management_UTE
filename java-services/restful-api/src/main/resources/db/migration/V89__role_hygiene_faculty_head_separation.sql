-- V89__role_hygiene_faculty_head_separation.sql
-- Role hygiene for the faculty-head (TRUONG_KHOA) role, enforcing the two
-- ownership rules the product guarantees:
--   R1 TRUONG_KHOA is a job title carried by a LECTURER account — never a
--      second hat on the academic-affairs (ADMIN) office. V31 seeded the demo
--      administrator with both roles as a demo shortcut; this migration
--      unwinds that shortcut (also removes it on any deployment where a real
--      ADMIN/SUPER_ADMIN account picked the role up the same way).
--   R2 ADMIN is the academic-affairs office role and is never assigned to a
--      lecturer, a faculty head included.
-- The faculty-head role stays on lecturer accounts only: the seeded faculty
-- head (truongkhoa-user-001) becomes a proper lecturer (Trưởng khoa) and one
-- new demo lecturer (Trưởng bộ môn) joins with the same single role pair.
-- Idempotent, scoped to synthetic demo rows (same discipline as V28/V31/V77).

-- R1: no account holding an administrative office role may also hold
-- TRUONG_KHOA. When 'TRUONG_KHOA' is absent the subquery yields NULL and the
-- DELETE is a no-op, so the statement is safe on any chain state.
DELETE FROM campuscore_auth."UserRole"
WHERE "roleId" = (SELECT "id" FROM campuscore_auth."Role" WHERE "name" = 'TRUONG_KHOA')
  AND "userId" IN (
      SELECT held."userId"
      FROM campuscore_auth."UserRole" held
      JOIN campuscore_auth."Role" office ON office."id" = held."roleId"
      WHERE office."name" IN ('ADMIN', 'SUPER_ADMIN')
  );

-- R2: no lecturer may hold the academic-affairs ADMIN role. academic."Lecturer"
-- is the single lecturer registry since V8 (campuscore_auth."Lecturer" became a
-- view over it).
DELETE FROM campuscore_auth."UserRole"
WHERE "roleId" = (SELECT "id" FROM campuscore_auth."Role" WHERE "name" = 'ADMIN')
  AND "userId" IN (SELECT lecturer."userId" FROM academic."Lecturer" lecturer);

-- The seeded faculty head becomes a proper lecturer: V31 created the account
-- and its TRUONG_KHOA role but no LECTURER role and no academic."Lecturer" row,
-- which contradicted R1's "faculty head is a job title on a lecturer" contract.
INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-truongkhoa-001-lecturer', 'truongkhoa-user-001', 'role-lecturer'
WHERE EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'truongkhoa-user-001')
  AND NOT EXISTS (
      SELECT 1 FROM campuscore_auth."UserRole"
      WHERE "userId" = 'truongkhoa-user-001' AND "roleId" = 'role-lecturer'
  );

INSERT INTO academic."Lecturer" ("id", "userId", "departmentId", "employeeId", "title", "specialization")
SELECT 'lecturer-profile-truongkhoa-001', 'truongkhoa-user-001', 'department-demo',
       'LEC-DEMO-TK001', 'Trưởng khoa', 'Quản trị học vụ và công nghệ phần mềm'
WHERE EXISTS (SELECT 1 FROM academic."Department" WHERE "id" = 'department-demo')
  AND NOT EXISTS (
      SELECT 1 FROM academic."Lecturer" WHERE "userId" = 'truongkhoa-user-001'
  );

-- New demo lecturer carrying the department-head job title. Seeded LOCKED like
-- the rest of the non-runbook @campuscore.demo population (V48 discipline):
-- credentials live in git history, so activation is a per-environment decision,
-- not a migration's. TRUONG_KHOA stays a standalone role — never ADMIN.
INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "status", "emailVerified")
SELECT 'truongkhoa-user-002', 'truongbomon@campuscore.demo',
       (SELECT "password" FROM campuscore_auth."User" WHERE "id" = 'lecturer-user' LIMIT 1),
       'Demo', 'Trưởng bộ môn CNTT', 'LOCKED', TRUE
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'truongkhoa-user-002');

INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-truongkhoa-002', 'truongkhoa-user-002', 'role-truong-khoa'
WHERE EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'truongkhoa-user-002')
  AND NOT EXISTS (
      SELECT 1 FROM campuscore_auth."UserRole"
      WHERE "userId" = 'truongkhoa-user-002' AND "roleId" = 'role-truong-khoa'
  );

INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-truongkhoa-002-lecturer', 'truongkhoa-user-002', 'role-lecturer'
WHERE EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'truongkhoa-user-002')
  AND NOT EXISTS (
      SELECT 1 FROM campuscore_auth."UserRole"
      WHERE "userId" = 'truongkhoa-user-002' AND "roleId" = 'role-lecturer'
  );

INSERT INTO academic."Lecturer" ("id", "userId", "departmentId", "employeeId", "title", "specialization")
SELECT 'lecturer-profile-truongkhoa-002', 'truongkhoa-user-002', 'department-demo',
       'LEC-DEMO-TK002', 'Trưởng bộ môn', 'Hệ thống thông tin doanh nghiệp'
WHERE EXISTS (SELECT 1 FROM academic."Department" WHERE "id" = 'department-demo')
  AND NOT EXISTS (
      SELECT 1 FROM academic."Lecturer" WHERE "userId" = 'truongkhoa-user-002'
  );
