-- V120__seed_truong_bo_mon_role.sql
-- Security checks already reference TRUONG_BO_MON (@PreAuthorize on thesis
-- mutation/round-read controllers, ThesisDepartmentScope, frontend portal
-- maps) but the Role row was never seeded — the same failure mode V109 fixed
-- for SUPER_ADMIN: role management fails with ROLE_NOT_FOUND and the
-- department-head path is dead end-to-end.
--
-- V89 seeded truongbomon@campuscore.demo ("Trưởng bộ môn CNTT") with the
-- faculty-head role 'role-truong-khoa' — unintended university-wide powers.
-- This migration grants the real department-head role and revokes the
-- faculty-head grant. The LECTURER pair stays: the lecturerId claim and
-- Lecturer profile are what ThesisDepartmentScope resolves the department
-- scope from. Idempotent throughout.

INSERT INTO campuscore_auth."Role" ("id", "name", "description", "isSystem")
SELECT 'role-truong-bo-mon', 'TRUONG_BO_MON',
       'Trưởng bộ môn (department head): thesis governance scoped to own department', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."Role" WHERE "name" = 'TRUONG_BO_MON'
);

-- Grant the real department-head role to the demo truongbomon account.
INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-truongbomon-002', 'truongkhoa-user-002', 'role-truong-bo-mon'
WHERE EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'truongkhoa-user-002')
  AND EXISTS (SELECT 1 FROM campuscore_auth."Role" WHERE "id" = 'role-truong-bo-mon')
  AND NOT EXISTS (
      SELECT 1 FROM campuscore_auth."UserRole"
      WHERE "userId" = 'truongkhoa-user-002' AND "roleId" = 'role-truong-bo-mon'
  );

-- Revoke the faculty-head grant that V89 mistakenly gave this account.
DELETE FROM campuscore_auth."UserRole"
WHERE "userId" = 'truongkhoa-user-002'
  AND "roleId" = 'role-truong-khoa'
  AND EXISTS (
      SELECT 1 FROM campuscore_auth."UserRole" ur
      WHERE ur."userId" = 'truongkhoa-user-002' AND ur."roleId" = 'role-truong-bo-mon'
  );
