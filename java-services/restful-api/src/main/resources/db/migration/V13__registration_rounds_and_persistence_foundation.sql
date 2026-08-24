-- Registration is deliberately introduced forward-only.  Existing rows are
-- inspected before new constraints are created; no migration step repairs data.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM academic."Enrollment"
        WHERE lower("status") IN ('active', 'enrolled')
        GROUP BY "studentId", "sectionId" HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V13 preflight failed: duplicate active academic.Enrollment rows';
    END IF;
    IF EXISTS (SELECT 1 FROM academic."Section" WHERE "capacity" < 0 OR "enrolledCount" < 0 OR "enrolledCount" > "capacity") THEN
        RAISE EXCEPTION 'V13 preflight failed: invalid academic.Section capacity/count';
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS academic."RegistrationRound" (
    "id" VARCHAR(120) PRIMARY KEY,
    "semesterId" VARCHAR(120) NOT NULL REFERENCES academic."Semester" ("id"),
    "status" VARCHAR(40) NOT NULL,
    "registrationStart" TIMESTAMPTZ NOT NULL,
    "registrationEnd" TIMESTAMPTZ NOT NULL,
    "addDropStart" TIMESTAMPTZ NOT NULL,
    "addDropEnd" TIMESTAMPTZ NOT NULL,
    "maxCredits" INTEGER NOT NULL DEFAULT 28,
    "institutionTimeZone" VARCHAR(80) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "version" BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT registration_round_window_valid CHECK ("registrationStart" < "registrationEnd" AND "addDropStart" < "addDropEnd"),
    CONSTRAINT registration_round_credit_limit_valid CHECK ("maxCredits" BETWEEN 1 AND 60),
    CONSTRAINT registration_round_semester_unique UNIQUE ("semesterId", "id")
);

CREATE INDEX IF NOT EXISTS registration_round_status_window_idx
    ON academic."RegistrationRound" ("status", "registrationStart", "registrationEnd");

CREATE TABLE IF NOT EXISTS academic."RegistrationCohortWindow" (
    "id" VARCHAR(120) PRIMARY KEY,
    "roundId" VARCHAR(120) NOT NULL REFERENCES academic."RegistrationRound" ("id") ON DELETE CASCADE,
    "cohortCode" VARCHAR(80) NOT NULL,
    "priorityRank" INTEGER NOT NULL DEFAULT 0,
    "windowStart" TIMESTAMPTZ NOT NULL,
    "windowEnd" TIMESTAMPTZ NOT NULL,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "version" BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT registration_cohort_window_valid CHECK ("windowStart" < "windowEnd" AND "priorityRank" >= 0),
    CONSTRAINT registration_cohort_round_code_unique UNIQUE ("roundId", "cohortCode")
);

CREATE TABLE IF NOT EXISTS academic."CourseRequirement" (
    "id" VARCHAR(120) PRIMARY KEY,
    "courseId" VARCHAR(120) NOT NULL REFERENCES academic."Course" ("id") ON DELETE CASCADE,
    "requiredCourseId" VARCHAR(120) NOT NULL REFERENCES academic."Course" ("id"),
    "requirementType" VARCHAR(20) NOT NULL,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT course_requirement_type_valid CHECK ("requirementType" IN ('PREREQUISITE', 'COREQUISITE')),
    CONSTRAINT course_requirement_not_self CHECK ("courseId" <> "requiredCourseId"),
    CONSTRAINT course_requirement_unique UNIQUE ("courseId", "requiredCourseId", "requirementType")
);

CREATE INDEX IF NOT EXISTS course_requirement_course_idx
    ON academic."CourseRequirement" ("courseId", "requirementType");

CREATE TABLE IF NOT EXISTS academic."EnrollmentOperation" (
    "id" VARCHAR(120) PRIMARY KEY,
    "studentId" VARCHAR(120) NOT NULL REFERENCES academic."Student" ("id"),
    "idempotencyKey" VARCHAR(120) NOT NULL,
    "canonicalRequestHash" CHAR(64) NOT NULL,
    "operationType" VARCHAR(20) NOT NULL,
    "state" VARCHAR(40) NOT NULL,
    "responseStatus" INTEGER,
    "responseBody" TEXT,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "completedAt" TIMESTAMPTZ,
    "version" BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT enrollment_operation_type_valid CHECK ("operationType" IN ('ENROLL', 'DROP')),
    CONSTRAINT enrollment_operation_hash_valid CHECK ("canonicalRequestHash" ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT enrollment_operation_key_unique UNIQUE ("studentId", "idempotencyKey")
);

CREATE INDEX IF NOT EXISTS enrollment_operation_state_idx
    ON academic."EnrollmentOperation" ("studentId", "state", "updatedAt");

CREATE TABLE IF NOT EXISTS academic."EnrollmentAudit" (
    "id" VARCHAR(120) PRIMARY KEY,
    "operationId" VARCHAR(120) REFERENCES academic."EnrollmentOperation" ("id"),
    "studentId" VARCHAR(120) NOT NULL REFERENCES academic."Student" ("id"),
    "sectionId" VARCHAR(120) NOT NULL REFERENCES academic."Section" ("id"),
    "action" VARCHAR(20) NOT NULL,
    "reasonCode" VARCHAR(80),
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT enrollment_audit_action_valid CHECK ("action" IN ('ENROLL', 'DROP', 'CANCEL', 'ARCHIVE'))
);

CREATE INDEX IF NOT EXISTS enrollment_audit_student_time_idx
    ON academic."EnrollmentAudit" ("studentId", "createdAt");

CREATE TABLE IF NOT EXISTS academic."RegistrationSlip" (
    "id" VARCHAR(120) PRIMARY KEY,
    "studentId" VARCHAR(120) NOT NULL REFERENCES academic."Student" ("id"),
    "roundId" VARCHAR(120) NOT NULL REFERENCES academic."RegistrationRound" ("id"),
    "contentHash" CHAR(64) NOT NULL,
    "generatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT registration_slip_hash_valid CHECK ("contentHash" ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT registration_slip_student_round_unique UNIQUE ("studentId", "roundId")
);
