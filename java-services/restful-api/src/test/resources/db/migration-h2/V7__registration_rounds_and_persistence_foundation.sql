CREATE SCHEMA IF NOT EXISTS academic;

CREATE TABLE IF NOT EXISTS academic."RegistrationRound" (
    "id" VARCHAR(120) PRIMARY KEY,
    "semesterId" VARCHAR(120) NOT NULL,
    "status" VARCHAR(40) NOT NULL,
    "registrationStart" TIMESTAMP WITH TIME ZONE NOT NULL,
    "registrationEnd" TIMESTAMP WITH TIME ZONE NOT NULL,
    "addDropStart" TIMESTAMP WITH TIME ZONE NOT NULL,
    "addDropEnd" TIMESTAMP WITH TIME ZONE NOT NULL,
    "maxCredits" INTEGER NOT NULL DEFAULT 28,
    "institutionTimeZone" VARCHAR(80) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "version" BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT registration_round_window_valid CHECK ("registrationStart" < "registrationEnd" AND "addDropStart" < "addDropEnd"),
    CONSTRAINT registration_round_credit_limit_valid CHECK ("maxCredits" BETWEEN 1 AND 60)
);

CREATE TABLE IF NOT EXISTS academic."RegistrationCohortWindow" (
    "id" VARCHAR(120) PRIMARY KEY,
    "roundId" VARCHAR(120) NOT NULL,
    "cohortCode" VARCHAR(80) NOT NULL,
    "priorityRank" INTEGER NOT NULL DEFAULT 0,
    "windowStart" TIMESTAMP WITH TIME ZONE NOT NULL,
    "windowEnd" TIMESTAMP WITH TIME ZONE NOT NULL,
    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "version" BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT registration_cohort_window_valid CHECK ("windowStart" < "windowEnd" AND "priorityRank" >= 0),
    CONSTRAINT registration_cohort_round_code_unique UNIQUE ("roundId", "cohortCode")
);

CREATE TABLE IF NOT EXISTS academic."CourseRequirement" (
    "id" VARCHAR(120) PRIMARY KEY,
    "courseId" VARCHAR(120) NOT NULL,
    "requiredCourseId" VARCHAR(120) NOT NULL,
    "requirementType" VARCHAR(20) NOT NULL,
    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT course_requirement_type_valid CHECK ("requirementType" IN ('PREREQUISITE', 'COREQUISITE')),
    CONSTRAINT course_requirement_not_self CHECK ("courseId" <> "requiredCourseId"),
    CONSTRAINT course_requirement_unique UNIQUE ("courseId", "requiredCourseId", "requirementType")
);

CREATE TABLE IF NOT EXISTS academic."EnrollmentOperation" (
    "id" VARCHAR(120) PRIMARY KEY,
    "studentId" VARCHAR(120) NOT NULL,
    "idempotencyKey" VARCHAR(120) NOT NULL,
    "canonicalRequestHash" CHAR(64) NOT NULL,
    "operationType" VARCHAR(20) NOT NULL,
    "state" VARCHAR(40) NOT NULL,
    "responseStatus" INTEGER,
    "responseBody" CLOB,
    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "completedAt" TIMESTAMP WITH TIME ZONE,
    "version" BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT enrollment_operation_type_valid CHECK ("operationType" IN ('ENROLL', 'DROP')),
    CONSTRAINT enrollment_operation_key_unique UNIQUE ("studentId", "idempotencyKey")
);

CREATE TABLE IF NOT EXISTS academic."EnrollmentAudit" (
    "id" VARCHAR(120) PRIMARY KEY,
    "operationId" VARCHAR(120),
    "studentId" VARCHAR(120) NOT NULL,
    "sectionId" VARCHAR(120) NOT NULL,
    "action" VARCHAR(20) NOT NULL,
    "reasonCode" VARCHAR(80),
    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT enrollment_audit_action_valid CHECK ("action" IN ('ENROLL', 'DROP', 'CANCEL', 'ARCHIVE'))
);

CREATE TABLE IF NOT EXISTS academic."RegistrationSlip" (
    "id" VARCHAR(120) PRIMARY KEY,
    "studentId" VARCHAR(120) NOT NULL,
    "roundId" VARCHAR(120) NOT NULL,
    "contentHash" CHAR(64) NOT NULL,
    "generatedAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT registration_slip_student_round_unique UNIQUE ("studentId", "roundId")
);
