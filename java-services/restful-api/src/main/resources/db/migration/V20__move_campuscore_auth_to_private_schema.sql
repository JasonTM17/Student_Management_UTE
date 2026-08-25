-- Supabase owns its auth schema. CampusCore identity remains Spring-owned in
-- a private application schema so hosted platform migrations stay isolated.
CREATE SCHEMA IF NOT EXISTS campuscore_auth;

-- V8 replaced the legacy Student/Lecturer tables with compatibility views.
-- Recreate them after relocating the underlying application-owned tables.
DROP VIEW IF EXISTS auth."Student";
DROP VIEW IF EXISTS auth."Lecturer";

ALTER TABLE IF EXISTS auth."User" SET SCHEMA campuscore_auth;
ALTER TABLE IF EXISTS auth."Role" SET SCHEMA campuscore_auth;
ALTER TABLE IF EXISTS auth."Permission" SET SCHEMA campuscore_auth;
ALTER TABLE IF EXISTS auth."UserRole" SET SCHEMA campuscore_auth;
ALTER TABLE IF EXISTS auth."RolePermission" SET SCHEMA campuscore_auth;
ALTER TABLE IF EXISTS auth."Session" SET SCHEMA campuscore_auth;
ALTER TABLE IF EXISTS auth."AuthChallenge" SET SCHEMA campuscore_auth;
ALTER TABLE IF EXISTS auth."AuthRateLimitBucket" SET SCHEMA campuscore_auth;

CREATE VIEW campuscore_auth."Student" AS
SELECT
    "id",
    "userId",
    "studentId",
    "curriculumId",
    "year",
    "status",
    "admissionDate",
    "createdAt",
    "updatedAt"
FROM academic."Student";

CREATE VIEW campuscore_auth."Lecturer" AS
SELECT
    "id",
    "userId",
    "departmentId",
    "employeeId",
    "isActive"
FROM academic."Lecturer";

COMMENT ON SCHEMA campuscore_auth IS
    'Private CampusCore identity, session and account-lifecycle schema';
