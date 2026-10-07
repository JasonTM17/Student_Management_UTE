-- H2 mirror of migration/V109__seed_super_admin_role.sql: keep the test
-- runtime's role catalog aligned so SUPER_ADMIN grants resolve in tests.

INSERT INTO campuscore_auth."Role" ("id", "name", "description", "isSystem")
SELECT 'role-super-admin', 'SUPER_ADMIN',
       'Super administrator: unrestricted governance access across modules', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."Role" WHERE "name" = 'SUPER_ADMIN'
);
