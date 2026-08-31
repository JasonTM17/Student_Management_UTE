-- Registration foundation: rounds, uniqueness, idempotency, slip columns.
-- Fail closed if duplicate active enrollments already exist.

DO $$
DECLARE
    duplicate_pairs integer;
    duplicate_courses integer;
BEGIN
    SELECT COUNT(*) INTO duplicate_pairs
    FROM (
        SELECT 1
        FROM academic."Enrollment"
        WHERE "status" IN ('ENROLLED', 'PENDING', 'CONFIRMED')
        GROUP BY "studentId", "sectionId"
        HAVING COUNT(*) > 1
    ) duplicates;
    IF duplicate_pairs > 0 THEN
        RAISE EXCEPTION 'duplicate active (student, section) enrollments exist; unique index refused';
    END IF;

    SELECT COUNT(*) INTO duplicate_courses
    FROM (
        SELECT e."studentId", s."courseId", e."semesterId"
        FROM academic."Enrollment" e
        JOIN academic."Section" s ON s."id" = e."sectionId"
        WHERE e."status" IN ('ENROLLED', 'PENDING', 'CONFIRMED')
        GROUP BY e."studentId", s."courseId", e."semesterId"
        HAVING COUNT(*) > 1
    ) duplicates;
    IF duplicate_courses > 0 THEN
        RAISE EXCEPTION 'duplicate active (student, course, semester) enrollments exist; unique index refused';
    END IF;
END
$$;

ALTER TABLE academic."Section"
    ADD COLUMN IF NOT EXISTS "version" INTEGER NOT NULL DEFAULT 0;

ALTER TABLE academic."Enrollment"
    ADD COLUMN IF NOT EXISTS "courseId" VARCHAR(120),
    ADD COLUMN IF NOT EXISTS "roundId" VARCHAR(120),
    ADD COLUMN IF NOT EXISTS "creditsSnapshot" INTEGER,
    ADD COLUMN IF NOT EXISTS "version" INTEGER NOT NULL DEFAULT 0;

UPDATE academic."Enrollment" AS enrollment
SET
    "courseId" = section."courseId",
    "creditsSnapshot" = course."credits"
FROM academic."Section" AS section
JOIN academic."Course" AS course ON course."id" = section."courseId"
WHERE enrollment."sectionId" = section."id"
  AND (enrollment."courseId" IS NULL OR enrollment."creditsSnapshot" IS NULL);

ALTER TABLE academic."Enrollment"
    ALTER COLUMN "courseId" SET NOT NULL,
    ALTER COLUMN "creditsSnapshot" SET NOT NULL;

ALTER TABLE academic."Enrollment"
    DROP CONSTRAINT IF EXISTS enrollment_course_fk;
ALTER TABLE academic."Enrollment"
    ADD CONSTRAINT enrollment_course_fk
    FOREIGN KEY ("courseId") REFERENCES academic."Course" ("id");

CREATE TABLE IF NOT EXISTS academic."RegistrationRound" (
    "id" VARCHAR(120) PRIMARY KEY,
    "semesterId" VARCHAR(120) NOT NULL REFERENCES academic."Semester" ("id"),
    "name" VARCHAR(180) NOT NULL,
    "kind" VARCHAR(40) NOT NULL,
    "status" VARCHAR(40) NOT NULL,
    "windowStart" TIMESTAMPTZ NOT NULL,
    "windowEnd" TIMESTAMPTZ NOT NULL,
    "creditLimit" INTEGER NOT NULL DEFAULT 28,
    "version" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."RegistrationRoundCohort" (
    "id" VARCHAR(120) PRIMARY KEY,
    "roundId" VARCHAR(120) NOT NULL REFERENCES academic."RegistrationRound" ("id") ON DELETE CASCADE,
    "curriculumId" VARCHAR(120),
    "year" INTEGER,
    "departmentId" VARCHAR(120),
    CONSTRAINT registration_round_cohort_unique UNIQUE ("roundId", "curriculumId", "year", "departmentId")
);

CREATE TABLE IF NOT EXISTS academic."CourseRequirement" (
    "id" VARCHAR(120) PRIMARY KEY,
    "courseId" VARCHAR(120) NOT NULL REFERENCES academic."Course" ("id") ON DELETE CASCADE,
    "requiredCourseId" VARCHAR(120) NOT NULL REFERENCES academic."Course" ("id"),
    "kind" VARCHAR(16) NOT NULL,
    "minLetterGrade" VARCHAR(16),
    CONSTRAINT course_requirement_unique UNIQUE ("courseId", "requiredCourseId", "kind")
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
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT registration_idempotency_owner_key UNIQUE ("ownerId", "idempotencyKey")
);

CREATE TABLE IF NOT EXISTS academic."EnrollmentEvent" (
    "id" VARCHAR(120) PRIMARY KEY,
    "enrollmentId" VARCHAR(120) NOT NULL,
    "studentId" VARCHAR(120) NOT NULL,
    "sectionId" VARCHAR(120) NOT NULL,
    "action" VARCHAR(16) NOT NULL,
    "actorId" VARCHAR(120) NOT NULL,
    "requestHash" CHAR(64),
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."RegistrationSlip" (
    "id" VARCHAR(120) PRIMARY KEY,
    "studentId" VARCHAR(120) NOT NULL REFERENCES academic."Student" ("id"),
    "semesterId" VARCHAR(120) NOT NULL REFERENCES academic."Semester" ("id"),
    "roundId" VARCHAR(120) NOT NULL REFERENCES academic."RegistrationRound" ("id"),
    "sha256" CHAR(64) NOT NULL,
    "byteSize" INTEGER NOT NULL,
    "payload" BYTEA NOT NULL,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT registration_slip_unique UNIQUE ("studentId", "semesterId", "roundId", "sha256")
);

CREATE UNIQUE INDEX IF NOT EXISTS academic_enrollment_active_student_section_uq
    ON academic."Enrollment" ("studentId", "sectionId")
    WHERE "status" IN ('ENROLLED', 'PENDING', 'CONFIRMED');

CREATE UNIQUE INDEX IF NOT EXISTS academic_enrollment_active_student_course_semester_uq
    ON academic."Enrollment" ("studentId", "courseId", "semesterId")
    WHERE "status" IN ('ENROLLED', 'PENDING', 'CONFIRMED');

INSERT INTO academic."RegistrationRound" (
    "id", "semesterId", "name", "kind", "status", "windowStart", "windowEnd", "creditLimit"
)
SELECT
    'round-registration-demo',
    "id",
    'Course registration',
    'REGISTRATION',
    'OPEN',
    COALESCE("registrationStart", "startDate"),
    COALESCE("registrationEnd", "endDate"),
    28
FROM academic."Semester"
WHERE "id" = 'semester-demo'
  AND NOT EXISTS (
      SELECT 1 FROM academic."RegistrationRound" WHERE "id" = 'round-registration-demo'
  );

INSERT INTO academic."RegistrationRound" (
    "id", "semesterId", "name", "kind", "status", "windowStart", "windowEnd", "creditLimit"
)
SELECT
    'round-add-drop-demo',
    "id",
    'Add / drop',
    'ADD_DROP',
    'OPEN',
    COALESCE("addDropStart", "startDate"),
    COALESCE("addDropEnd", "endDate"),
    28
FROM academic."Semester"
WHERE "id" = 'semester-demo'
  AND NOT EXISTS (
      SELECT 1 FROM academic."RegistrationRound" WHERE "id" = 'round-add-drop-demo'
  );

UPDATE academic."Enrollment" AS enrollment
SET "roundId" = 'round-registration-demo'
WHERE enrollment."semesterId" = 'semester-demo'
  AND enrollment."roundId" IS NULL;
