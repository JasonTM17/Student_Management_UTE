-- V11__thesis_governance_seeds.sql (H2 twin of production V31)
-- Faculty-head role; grants are conditional because H2 test databases may not
-- seed the demo administrator row.

INSERT INTO campuscore_auth."Role" ("id", "name", "description", "isSystem", "createdAt", "updatedAt")
SELECT 'role-truong-khoa', 'TRUONG_KHOA',
       'Faculty head: owns thesis rounds, councils and result publication', TRUE,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."Role" WHERE "name" = 'TRUONG_KHOA'
);

INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-admin-truongkhoa', 'admin-user', 'role-truong-khoa'
WHERE EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'admin-user')
  AND NOT EXISTS (
      SELECT 1 FROM campuscore_auth."UserRole"
      WHERE "userId" = 'admin-user' AND "roleId" = 'role-truong-khoa'
  );
