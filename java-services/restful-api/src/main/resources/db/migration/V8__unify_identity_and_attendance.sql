ALTER TABLE academic."Student"
    DROP CONSTRAINT IF EXISTS "Student_userId_fkey";
ALTER TABLE academic."Student"
    ADD CONSTRAINT "Student_userId_fkey"
    FOREIGN KEY ("userId") REFERENCES campuscore_auth."User" ("id") ON DELETE CASCADE;

ALTER TABLE academic."Lecturer"
    DROP CONSTRAINT IF EXISTS "Lecturer_userId_fkey";
ALTER TABLE academic."Lecturer"
    ADD CONSTRAINT "Lecturer_userId_fkey"
    FOREIGN KEY ("userId") REFERENCES campuscore_auth."User" ("id") ON DELETE CASCADE;

DROP TABLE IF EXISTS campuscore_auth."Student";
DROP TABLE IF EXISTS campuscore_auth."Lecturer";
DROP TABLE IF EXISTS academic."User";

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

CREATE TABLE IF NOT EXISTS academic."Attendance" (
    "id" VARCHAR(120) PRIMARY KEY,
    "studentId" VARCHAR(120) NOT NULL REFERENCES academic."Student" ("id") ON DELETE CASCADE,
    "sectionId" VARCHAR(120) NOT NULL REFERENCES academic."Section" ("id") ON DELETE CASCADE,
    "date" TIMESTAMPTZ NOT NULL,
    "status" VARCHAR(40) NOT NULL,
    "notes" VARCHAR(1000),
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT academic_attendance_student_section_date_unique
        UNIQUE ("studentId", "sectionId", "date")
);

CREATE INDEX IF NOT EXISTS academic_attendance_student_idx
    ON academic."Attendance" ("studentId", "date");
CREATE INDEX IF NOT EXISTS academic_attendance_section_idx
    ON academic."Attendance" ("sectionId", "date");
CREATE UNIQUE INDEX IF NOT EXISTS campuscore_auth_session_refresh_unique
    ON campuscore_auth."Session" ("refreshToken");

ALTER TABLE thesis.thesis_registration_round
    DROP COLUMN IF EXISTS defense_date;
