-- Assistant H2 path has no academic catalog. Create registration tables without
-- catalog foreign keys so V1–V6 still apply. Unique indexes on Enrollment are
-- owned by Postgres V14 and by academic MockMvc fixtures.

CREATE SCHEMA IF NOT EXISTS academic;

CREATE TABLE IF NOT EXISTS academic."RegistrationRound" (
    "id" VARCHAR(120) PRIMARY KEY,
    "semesterId" VARCHAR(120) NOT NULL,
    "name" VARCHAR(180) NOT NULL,
    "kind" VARCHAR(40) NOT NULL,
    "status" VARCHAR(40) NOT NULL,
    "windowStart" TIMESTAMP NOT NULL,
    "windowEnd" TIMESTAMP NOT NULL,
    "creditLimit" INTEGER NOT NULL DEFAULT 28,
    "version" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."RegistrationRoundCohort" (
    "id" VARCHAR(120) PRIMARY KEY,
    "roundId" VARCHAR(120) NOT NULL,
    "curriculumId" VARCHAR(120),
    "year" INTEGER,
    "departmentId" VARCHAR(120)
);

CREATE TABLE IF NOT EXISTS academic."CourseRequirement" (
    "id" VARCHAR(120) PRIMARY KEY,
    "courseId" VARCHAR(120) NOT NULL,
    "requiredCourseId" VARCHAR(120) NOT NULL,
    "kind" VARCHAR(16) NOT NULL,
    "minLetterGrade" VARCHAR(16)
);

CREATE TABLE IF NOT EXISTS academic."RegistrationIdempotency" (
    "id" VARCHAR(120) PRIMARY KEY,
    "ownerId" VARCHAR(120) NOT NULL,
    "idempotencyKey" VARCHAR(200) NOT NULL,
    "requestHash" CHAR(64) NOT NULL,
    "state" VARCHAR(40) NOT NULL,
    "enrollmentId" VARCHAR(120),
    "responseBody" TEXT,
    "slipSha256" CHAR(64),
    "version" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."EnrollmentEvent" (
    "id" VARCHAR(120) PRIMARY KEY,
    "enrollmentId" VARCHAR(120) NOT NULL,
    "studentId" VARCHAR(120) NOT NULL,
    "sectionId" VARCHAR(120) NOT NULL,
    "action" VARCHAR(16) NOT NULL,
    "actorId" VARCHAR(120) NOT NULL,
    "requestHash" CHAR(64),
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."RegistrationSlip" (
    "id" VARCHAR(120) PRIMARY KEY,
    "studentId" VARCHAR(120) NOT NULL,
    "semesterId" VARCHAR(120) NOT NULL,
    "roundId" VARCHAR(120) NOT NULL,
    "sha256" CHAR(64) NOT NULL,
    "byteSize" INTEGER NOT NULL,
    "payload" BYTEA NOT NULL,
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
