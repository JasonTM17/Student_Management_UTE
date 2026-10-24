-- Minimal H2 mirrors of the academic catalog tables the transcript read query
-- joins. Thesis eligibility evaluation (V143 feature) runs this query inside
-- group mutation and read flows, so flyway-backed tests need the tables to
-- exist even when no fixture seeds academic history. Every non-key column is
-- nullable on purpose: persistence fixtures insert partial rows and own the
-- real constraint checks; the mirror only provides the columns the join reads.

CREATE TABLE IF NOT EXISTS academic."Student" (
    "id" VARCHAR(120) PRIMARY KEY,
    "userId" VARCHAR(120),
    "studentId" VARCHAR(120),
    "curriculumId" VARCHAR(120),
    "year" INTEGER,
    "status" VARCHAR(40),
    "admissionDate" TIMESTAMP,
    "createdAt" TIMESTAMP,
    "updatedAt" TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."AcademicYear" (
    "id" VARCHAR(120) PRIMARY KEY,
    "year" INTEGER,
    "startDate" TIMESTAMP,
    "endDate" TIMESTAMP,
    "isCurrent" BOOLEAN,
    "createdAt" TIMESTAMP,
    "updatedAt" TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."Semester" (
    "id" VARCHAR(120) PRIMARY KEY,
    "name" VARCHAR(120),
    "nameEn" VARCHAR(120),
    "nameVi" VARCHAR(120),
    "type" VARCHAR(40),
    "academicYearId" VARCHAR(120),
    "startDate" TIMESTAMP,
    "endDate" TIMESTAMP,
    "registrationStart" TIMESTAMP,
    "registrationEnd" TIMESTAMP,
    "addDropStart" TIMESTAMP,
    "addDropEnd" TIMESTAMP,
    "status" VARCHAR(40),
    "createdAt" TIMESTAMP,
    "updatedAt" TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."Course" (
    "id" VARCHAR(120) PRIMARY KEY,
    "code" VARCHAR(40),
    "name" VARCHAR(200),
    "nameEn" VARCHAR(200),
    "nameVi" VARCHAR(200),
    "description" VARCHAR(1000),
    "descriptionEn" VARCHAR(1000),
    "descriptionVi" VARCHAR(1000),
    "credits" INTEGER,
    "departmentId" VARCHAR(120),
    "semesterId" VARCHAR(120),
    "isActive" BOOLEAN,
    "createdAt" TIMESTAMP,
    "updatedAt" TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."Lecturer" (
    "id" VARCHAR(120) PRIMARY KEY,
    "userId" VARCHAR(120),
    "departmentId" VARCHAR(120),
    "employeeId" VARCHAR(120),
    "isActive" BOOLEAN
);

CREATE TABLE IF NOT EXISTS academic."GradeItem" (
    "id" VARCHAR(120) PRIMARY KEY,
    "sectionId" VARCHAR(120),
    "name" VARCHAR(120),
    "type" VARCHAR(40),
    "maxScore" DECIMAL(5, 2),
    "weight" DECIMAL(5, 2),
    "gradedAt" TIMESTAMP,
    "createdAt" TIMESTAMP
);

-- The twin's academic."Section" is a lean reference-check stub (V117); add the
-- columns the transcript join reads.
ALTER TABLE academic."Section" ADD COLUMN IF NOT EXISTS "sectionNumber" VARCHAR(40);
ALTER TABLE academic."Section" ADD COLUMN IF NOT EXISTS "courseId" VARCHAR(120);
ALTER TABLE academic."Section" ADD COLUMN IF NOT EXISTS "semesterId" VARCHAR(120);
ALTER TABLE academic."Section" ADD COLUMN IF NOT EXISTS "classroomId" VARCHAR(120);
ALTER TABLE academic."Section" ADD COLUMN IF NOT EXISTS "status" VARCHAR(40);
