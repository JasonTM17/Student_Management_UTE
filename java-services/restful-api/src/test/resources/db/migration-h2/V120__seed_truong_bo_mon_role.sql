-- H2 twin of production V120__seed_truong_bo_mon_role.sql.
--
-- Seeds the TRUONG_BO_MON role referenced by thesis department-head guards and
-- moves the demo truongbomon account off the V89-mistaken faculty-head grant.

INSERT INTO campuscore_auth."Role" ("id", "name", "description", "isSystem")
SELECT 'role-truong-bo-mon', 'TRUONG_BO_MON',
       'Trưởng bộ môn (department head): thesis governance scoped to own department', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."Role" WHERE "name" = 'TRUONG_BO_MON'
);

INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-truongbomon-002', 'truongkhoa-user-002', 'role-truong-bo-mon'
WHERE EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'truongkhoa-user-002')
  AND EXISTS (SELECT 1 FROM campuscore_auth."Role" WHERE "id" = 'role-truong-bo-mon')
  AND NOT EXISTS (
      SELECT 1 FROM campuscore_auth."UserRole"
      WHERE "userId" = 'truongkhoa-user-002' AND "roleId" = 'role-truong-bo-mon'
  );

DELETE FROM campuscore_auth."UserRole"
WHERE "userId" = 'truongkhoa-user-002'
  AND "roleId" = 'role-truong-khoa'
  AND EXISTS (
      SELECT 1 FROM campuscore_auth."UserRole" ur
      WHERE ur."userId" = 'truongkhoa-user-002' AND ur."roleId" = 'role-truong-bo-mon'
  );
