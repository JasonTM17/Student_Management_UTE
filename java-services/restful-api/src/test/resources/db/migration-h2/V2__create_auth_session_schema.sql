CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE IF NOT EXISTS auth."User" (
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

CREATE TABLE IF NOT EXISTS auth."Role" (
    "id" VARCHAR(120) PRIMARY KEY,
    "name" VARCHAR(80) UNIQUE NOT NULL,
    "description" VARCHAR(500),
    "isSystem" BOOLEAN NOT NULL DEFAULT FALSE,
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth."Permission" (
    "id" VARCHAR(120) PRIMARY KEY,
    "name" VARCHAR(160) UNIQUE NOT NULL,
    "description" VARCHAR(500),
    "module" VARCHAR(80) NOT NULL,
    "action" VARCHAR(80) NOT NULL,
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth."UserRole" (
    "id" VARCHAR(120) PRIMARY KEY,
    "userId" VARCHAR(120) NOT NULL REFERENCES auth."User" ("id") ON DELETE CASCADE,
    "roleId" VARCHAR(120) NOT NULL REFERENCES auth."Role" ("id") ON DELETE CASCADE,
    CONSTRAINT auth_user_role_unique UNIQUE ("userId", "roleId")
);

CREATE TABLE IF NOT EXISTS auth."RolePermission" (
    "id" VARCHAR(120) PRIMARY KEY,
    "roleId" VARCHAR(120) NOT NULL REFERENCES auth."Role" ("id") ON DELETE CASCADE,
    "permissionId" VARCHAR(120) NOT NULL REFERENCES auth."Permission" ("id") ON DELETE CASCADE,
    CONSTRAINT auth_role_permission_unique UNIQUE ("roleId", "permissionId")
);

CREATE TABLE IF NOT EXISTS auth."Student" (
    "id" VARCHAR(120) PRIMARY KEY,
    "userId" VARCHAR(120) UNIQUE NOT NULL REFERENCES auth."User" ("id") ON DELETE CASCADE,
    "studentId" VARCHAR(120) UNIQUE NOT NULL,
    "curriculumId" VARCHAR(120) NOT NULL,
    "year" INTEGER NOT NULL,
    "status" VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    "admissionDate" TIMESTAMP NOT NULL,
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth."Lecturer" (
    "id" VARCHAR(120) PRIMARY KEY,
    "userId" VARCHAR(120) UNIQUE NOT NULL REFERENCES auth."User" ("id") ON DELETE CASCADE,
    "departmentId" VARCHAR(120) NOT NULL,
    "employeeId" VARCHAR(120) UNIQUE NOT NULL,
    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS auth."Session" (
    "id" VARCHAR(120) PRIMARY KEY,
    "userId" VARCHAR(120) NOT NULL REFERENCES auth."User" ("id") ON DELETE CASCADE,
    "refreshToken" VARCHAR(200) NOT NULL,
    "userAgent" VARCHAR(500),
    "ipAddress" VARCHAR(80),
    "expiresAt" TIMESTAMP NOT NULL,
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS auth_session_user_idx ON auth."Session" ("userId");
CREATE INDEX IF NOT EXISTS auth_session_refresh_idx ON auth."Session" ("refreshToken");

INSERT INTO auth."Role" ("id", "name", "description", "isSystem", "createdAt", "updatedAt")
SELECT 'role-student', 'STUDENT', 'Course demo student role', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM auth."Role" WHERE "name" = 'STUDENT');
INSERT INTO auth."Role" ("id", "name", "description", "isSystem", "createdAt", "updatedAt")
SELECT 'role-lecturer', 'LECTURER', 'Course demo lecturer role', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM auth."Role" WHERE "name" = 'LECTURER');
INSERT INTO auth."Role" ("id", "name", "description", "isSystem", "createdAt", "updatedAt")
SELECT 'role-admin', 'ADMIN', 'Course demo administrator role', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM auth."Role" WHERE "name" = 'ADMIN');

INSERT INTO auth."Permission" ("id", "name", "description", "module", "action")
SELECT 'permission-thesis-read', 'thesis:read', 'Read thesis data', 'thesis', 'read'
WHERE NOT EXISTS (SELECT 1 FROM auth."Permission" WHERE "name" = 'thesis:read');
INSERT INTO auth."Permission" ("id", "name", "description", "module", "action")
SELECT 'permission-academic-read', 'academic:read', 'Read academic data', 'academic', 'read'
WHERE NOT EXISTS (SELECT 1 FROM auth."Permission" WHERE "name" = 'academic:read');

INSERT INTO auth."User" ("id", "email", "password", "firstName", "lastName", "status")
SELECT 'student-user', 'student@campuscore.edu', '$2a$10$raV9MB3Qmj1Rbu2Rmo1vNup7VsC2OM3AqmcTcTLzbNyMyI4r2rJBe', 'Demo', 'Student', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM auth."User" WHERE "email" = 'student@campuscore.edu');
INSERT INTO auth."Student" ("id", "userId", "studentId", "curriculumId", "year", "admissionDate")
SELECT 'student-profile', 'student-user', 'CS-DEMO-001', 'curriculum-demo', 2, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM auth."Student" WHERE "userId" = 'student-user');
INSERT INTO auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-student', 'student-user', 'role-student'
WHERE NOT EXISTS (SELECT 1 FROM auth."UserRole" WHERE "userId" = 'student-user' AND "roleId" = 'role-student');
INSERT INTO auth."RolePermission" ("id", "roleId", "permissionId")
SELECT 'role-permission-student', 'role-student', 'permission-academic-read'
WHERE NOT EXISTS (SELECT 1 FROM auth."RolePermission" WHERE "roleId" = 'role-student' AND "permissionId" = 'permission-academic-read');
INSERT INTO auth."RolePermission" ("id", "roleId", "permissionId")
SELECT 'role-permission-thesis', 'role-student', 'permission-thesis-read'
WHERE NOT EXISTS (SELECT 1 FROM auth."RolePermission" WHERE "roleId" = 'role-student' AND "permissionId" = 'permission-thesis-read');
