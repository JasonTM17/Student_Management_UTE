-- Minimal H2 mirrors of production history tables that account-profile
-- removal must consult before a destructive role conversion. Columns are the
-- ones the reference checks read; the production tables carry more.
-- Test-only scaffolding: these tables already exist in production from earlier
-- migrations; V117 was picked because prod V115/V116 are unrelated files.
CREATE TABLE IF NOT EXISTS academic."Curriculum" (
    "id" VARCHAR(120) PRIMARY KEY,
    "name" VARCHAR(180) NOT NULL DEFAULT '',
    "departmentId" VARCHAR(120) NOT NULL
);

CREATE TABLE IF NOT EXISTS academic."Attendance" (
    "id" VARCHAR(120) PRIMARY KEY,
    "studentId" VARCHAR(120)
);

CREATE TABLE IF NOT EXISTS academic."Section" (
    "id" VARCHAR(120) PRIMARY KEY,
    "lecturerId" VARCHAR(120)
);

CREATE TABLE IF NOT EXISTS academic.conduct_semester_score (
    id VARCHAR(120) PRIMARY KEY,
    student_id VARCHAR(120)
);

CREATE TABLE IF NOT EXISTS academic.conduct_activity (
    id VARCHAR(120) PRIMARY KEY,
    student_id VARCHAR(120)
);

CREATE TABLE IF NOT EXISTS engagement."Announcement" (
    "id" VARCHAR(120) PRIMARY KEY,
    "lecturerId" VARCHAR(120)
);
