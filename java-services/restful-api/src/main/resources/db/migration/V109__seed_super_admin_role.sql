-- V109__seed_super_admin_role.sql
-- Security checks already reference SUPER_ADMIN (hasAnyRole expressions,
-- ThesisSupervisorService.isStaff, frontend role maps) but the Role row was
-- never seeded, so granting it through admin role management fails with
-- ROLE_NOT_FOUND. Idempotent insert mirrors V31 style.

INSERT INTO campuscore_auth."Role" ("id", "name", "description", "isSystem")
SELECT 'role-super-admin', 'SUPER_ADMIN',
       'Super administrator: unrestricted governance access across modules', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."Role" WHERE "name" = 'SUPER_ADMIN'
);
