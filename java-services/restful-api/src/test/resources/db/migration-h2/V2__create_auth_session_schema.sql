CREATE SCHEMA IF NOT EXISTS campuscore_auth;

CREATE TABLE IF NOT EXISTS campuscore_auth."User" (
    "id" VARCHAR(120) PRIMARY KEY,
    "email" VARCHAR(320) UNIQUE NOT NULL,
    "password" VARCHAR(200) NOT NULL,
    "firstName" VARCHAR(120) NOT NULL,
    "lastName" VARCHAR(120) NOT NULL,
    "phone" VARCHAR(80),
    "gender" VARCHAR(40),
    "dateOfBirth" TIMESTAMP,
    "address" VARCHAR(500),
    "avatar" VARCHAR(500),
    "status" VARCHAR(40) NOT NULL,
    "emailVerified" BOOLEAN NOT NULL DEFAULT FALSE,
    "isSuperAdmin" BOOLEAN NOT NULL DEFAULT FALSE,
    "failedLoginAttempts" INTEGER NOT NULL DEFAULT 0,
    "lockedUntil" TIMESTAMP,
    "lastLoginAt" TIMESTAMP,
    "passwordChangedAt" TIMESTAMP,
    "refreshToken" VARCHAR(200),
    "resetToken" VARCHAR(200),
    "resetExpires" TIMESTAMP,
    "verificationToken" VARCHAR(200),
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS campuscore_auth."Role" (
    "id" VARCHAR(120) PRIMARY KEY,
    "name" VARCHAR(80) UNIQUE NOT NULL,
    "description" VARCHAR(500),
    "isSystem" BOOLEAN NOT NULL DEFAULT FALSE,
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS campuscore_auth."Permission" (
    "id" VARCHAR(120) PRIMARY KEY,
    "name" VARCHAR(160) UNIQUE NOT NULL,
    "description" VARCHAR(500),
    "module" VARCHAR(80) NOT NULL,
    "action" VARCHAR(80) NOT NULL,
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS campuscore_auth."UserRole" (
    "id" VARCHAR(120) PRIMARY KEY,
    "userId" VARCHAR(120) NOT NULL REFERENCES campuscore_auth."User" ("id") ON DELETE CASCADE,
    "roleId" VARCHAR(120) NOT NULL REFERENCES campuscore_auth."Role" ("id") ON DELETE CASCADE,
    CONSTRAINT campuscore_auth_user_role_unique UNIQUE ("userId", "roleId")
);

CREATE TABLE IF NOT EXISTS campuscore_auth."RolePermission" (
    "id" VARCHAR(120) PRIMARY KEY,
    "roleId" VARCHAR(120) NOT NULL REFERENCES campuscore_auth."Role" ("id") ON DELETE CASCADE,
    "permissionId" VARCHAR(120) NOT NULL REFERENCES campuscore_auth."Permission" ("id") ON DELETE CASCADE,
    CONSTRAINT campuscore_auth_role_permission_unique UNIQUE ("roleId", "permissionId")
);

CREATE TABLE IF NOT EXISTS campuscore_auth."Student" (
    "id" VARCHAR(120) PRIMARY KEY,
    "userId" VARCHAR(120) UNIQUE NOT NULL REFERENCES campuscore_auth."User" ("id") ON DELETE CASCADE,
    "studentId" VARCHAR(120) UNIQUE NOT NULL,
    "curriculumId" VARCHAR(120) NOT NULL,
    "year" INTEGER NOT NULL,
    "status" VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    "admissionDate" TIMESTAMP NOT NULL,
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS campuscore_auth."Lecturer" (
    "id" VARCHAR(120) PRIMARY KEY,
    "userId" VARCHAR(120) UNIQUE NOT NULL REFERENCES campuscore_auth."User" ("id") ON DELETE CASCADE,
    "departmentId" VARCHAR(120) NOT NULL,
    "employeeId" VARCHAR(120) UNIQUE NOT NULL,
    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS campuscore_auth."Session" (
    "id" VARCHAR(120) PRIMARY KEY,
    "userId" VARCHAR(120) NOT NULL REFERENCES campuscore_auth."User" ("id") ON DELETE CASCADE,
    "refreshToken" VARCHAR(200) NOT NULL,
    "userAgent" VARCHAR(500),
    "ipAddress" VARCHAR(80),
    "expiresAt" TIMESTAMP NOT NULL,
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS campuscore_auth_session_user_idx ON campuscore_auth."Session" ("userId");
CREATE INDEX IF NOT EXISTS campuscore_auth_session_refresh_idx ON campuscore_auth."Session" ("refreshToken");

INSERT INTO campuscore_auth."Role" ("id", "name", "description", "isSystem", "createdAt", "updatedAt")
SELECT 'role-student', 'STUDENT', 'Course demo student role', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."Role" WHERE "name" = 'STUDENT');
INSERT INTO campuscore_auth."Role" ("id", "name", "description", "isSystem", "createdAt", "updatedAt")
SELECT 'role-lecturer', 'LECTURER', 'Course demo lecturer role', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."Role" WHERE "name" = 'LECTURER');
INSERT INTO campuscore_auth."Role" ("id", "name", "description", "isSystem", "createdAt", "updatedAt")
SELECT 'role-admin', 'ADMIN', 'Course demo administrator role', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."Role" WHERE "name" = 'ADMIN');

INSERT INTO campuscore_auth."Permission" ("id", "name", "description", "module", "action", "createdAt")
SELECT 'permission-thesis-read', 'thesis:read', 'Read thesis data', 'thesis', 'read', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."Permission" WHERE "name" = 'thesis:read');
INSERT INTO campuscore_auth."Permission" ("id", "name", "description", "module", "action", "createdAt")
SELECT 'permission-academic-read', 'academic:read', 'Read academic data', 'academic', 'read', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."Permission" WHERE "name" = 'academic:read');

INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "status")
SELECT 'student-user', 'student@campuscore.edu', '$2a$10$raV9MB3Qmj1Rbu2Rmo1vNup7VsC2OM3AqmcTcTLzbNyMyI4r2rJBe', 'Demo', 'Student', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "email" = 'student@campuscore.edu');
INSERT INTO campuscore_auth."Student" ("id", "userId", "studentId", "curriculumId", "year", "admissionDate")
SELECT 'student-profile', 'student-user', 'CS-DEMO-001', 'curriculum-demo', 2, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."Student" WHERE "userId" = 'student-user');
INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-student', 'student-user', 'role-student'
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."UserRole" WHERE "userId" = 'student-user' AND "roleId" = 'role-student');
INSERT INTO campuscore_auth."RolePermission" ("id", "roleId", "permissionId")
SELECT 'role-permission-student', 'role-student', 'permission-academic-read'
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."RolePermission" WHERE "roleId" = 'role-student' AND "permissionId" = 'permission-academic-read');
INSERT INTO campuscore_auth."RolePermission" ("id", "roleId", "permissionId")
SELECT 'role-permission-thesis', 'role-student', 'permission-thesis-read'
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."RolePermission" WHERE "roleId" = 'role-student' AND "permissionId" = 'permission-thesis-read');
