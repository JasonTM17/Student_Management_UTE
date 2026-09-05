CREATE SCHEMA IF NOT EXISTS academic;
CREATE SCHEMA IF NOT EXISTS engagement;
CREATE SCHEMA IF NOT EXISTS notifications;

CREATE TABLE IF NOT EXISTS academic."User" (
    "id" VARCHAR(120) PRIMARY KEY,
    "email" VARCHAR(320) UNIQUE NOT NULL,
    "firstName" VARCHAR(120) NOT NULL,
    "lastName" VARCHAR(120) NOT NULL,
    "status" VARCHAR(40) NOT NULL,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."Faculty" (
    "id" VARCHAR(120) PRIMARY KEY,
    "name" VARCHAR(180) NOT NULL,
    "nameEn" VARCHAR(180),
    "nameVi" VARCHAR(180),
    "code" VARCHAR(40) NOT NULL,
    "description" VARCHAR(1000),
    "descriptionEn" VARCHAR(1000),
    "descriptionVi" VARCHAR(1000),
    "dean" VARCHAR(160),
    "phone" VARCHAR(80),
    "email" VARCHAR(320),
    "building" VARCHAR(160),
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS academic."Department" (
    "id" VARCHAR(120) PRIMARY KEY,
    "name" VARCHAR(180) NOT NULL,
    "nameEn" VARCHAR(180),
    "nameVi" VARCHAR(180),
    "code" VARCHAR(40) NOT NULL,
    "description" VARCHAR(1000),
    "descriptionEn" VARCHAR(1000),
    "descriptionVi" VARCHAR(1000),
    "chair" VARCHAR(160),
    "phone" VARCHAR(80),
    "email" VARCHAR(320),
    "building" VARCHAR(160),
    "facultyId" VARCHAR(120) NOT NULL REFERENCES academic."Faculty" ("id"),
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS academic."AcademicYear" (
    "id" VARCHAR(120) PRIMARY KEY,
    "year" INTEGER NOT NULL,
    "startDate" TIMESTAMPTZ NOT NULL,
    "endDate" TIMESTAMPTZ NOT NULL,
    "isCurrent" BOOLEAN NOT NULL DEFAULT FALSE,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."Semester" (
    "id" VARCHAR(120) PRIMARY KEY,
    "name" VARCHAR(180) NOT NULL,
    "nameEn" VARCHAR(180),
    "nameVi" VARCHAR(180),
    "type" VARCHAR(40) NOT NULL,
    "academicYearId" VARCHAR(120) NOT NULL REFERENCES academic."AcademicYear" ("id"),
    "startDate" TIMESTAMPTZ NOT NULL,
    "endDate" TIMESTAMPTZ NOT NULL,
    "registrationStart" TIMESTAMPTZ,
    "registrationEnd" TIMESTAMPTZ,
    "addDropStart" TIMESTAMPTZ,
    "addDropEnd" TIMESTAMPTZ,
    "status" VARCHAR(40) NOT NULL,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."Curriculum" (
    "id" VARCHAR(120) PRIMARY KEY,
    "name" VARCHAR(180) NOT NULL,
    "nameEn" VARCHAR(180),
    "nameVi" VARCHAR(180),
    "code" VARCHAR(40) NOT NULL,
    "departmentId" VARCHAR(120) NOT NULL REFERENCES academic."Department" ("id"),
    "academicYearId" VARCHAR(120) NOT NULL REFERENCES academic."AcademicYear" ("id"),
    "semesterId" VARCHAR(120),
    "totalCredits" INTEGER NOT NULL DEFAULT 0,
    "description" VARCHAR(1000),
    "descriptionEn" VARCHAR(1000),
    "descriptionVi" VARCHAR(1000),
    "isActive" BOOLEAN NOT NULL DEFAULT TRUE,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."CurriculumCourse" (
    "id" VARCHAR(120) PRIMARY KEY,
    "curriculumId" VARCHAR(120) NOT NULL REFERENCES academic."Curriculum" ("id") ON DELETE CASCADE,
    "courseId" VARCHAR(120) NOT NULL,
    "year" INTEGER NOT NULL,
    "semester" INTEGER NOT NULL,
    "isMandatory" BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS academic."Course" (
    "id" VARCHAR(120) PRIMARY KEY,
    "code" VARCHAR(60) NOT NULL,
    "name" VARCHAR(240) NOT NULL,
    "nameEn" VARCHAR(240),
    "nameVi" VARCHAR(240),
    "description" VARCHAR(1000),
    "descriptionEn" VARCHAR(1000),
    "descriptionVi" VARCHAR(1000),
    "credits" INTEGER NOT NULL,
    "departmentId" VARCHAR(120) NOT NULL REFERENCES academic."Department" ("id"),
    "semesterId" VARCHAR(120),
    "isActive" BOOLEAN NOT NULL DEFAULT TRUE,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."Classroom" (
    "id" VARCHAR(120) PRIMARY KEY,
    "building" VARCHAR(160) NOT NULL,
    "roomNumber" VARCHAR(80) NOT NULL,
    "capacity" INTEGER NOT NULL,
    "type" VARCHAR(80) NOT NULL,
    "isActive" BOOLEAN NOT NULL DEFAULT TRUE,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."Lecturer" (
    "id" VARCHAR(120) PRIMARY KEY,
    "userId" VARCHAR(120) NOT NULL UNIQUE REFERENCES academic."User" ("id"),
    "departmentId" VARCHAR(120) NOT NULL REFERENCES academic."Department" ("id"),
    "employeeId" VARCHAR(120) NOT NULL UNIQUE,
    "title" VARCHAR(160),
    "specialization" VARCHAR(240),
    "office" VARCHAR(120),
    "phone" VARCHAR(80),
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS academic."Student" (
    "id" VARCHAR(120) PRIMARY KEY,
    "userId" VARCHAR(120) NOT NULL UNIQUE REFERENCES academic."User" ("id"),
    "studentId" VARCHAR(120) NOT NULL UNIQUE,
    "curriculumId" VARCHAR(120) NOT NULL REFERENCES academic."Curriculum" ("id"),
    "year" INTEGER NOT NULL,
    "status" VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    "admissionDate" TIMESTAMPTZ NOT NULL,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."Section" (
    "id" VARCHAR(120) PRIMARY KEY,
    "sectionNumber" VARCHAR(80) NOT NULL,
    "courseId" VARCHAR(120) NOT NULL REFERENCES academic."Course" ("id"),
    "semesterId" VARCHAR(120) NOT NULL REFERENCES academic."Semester" ("id"),
    "lecturerId" VARCHAR(120) REFERENCES academic."Lecturer" ("id"),
    "classroomId" VARCHAR(120) REFERENCES academic."Classroom" ("id"),
    "capacity" INTEGER NOT NULL,
    "enrolledCount" INTEGER NOT NULL DEFAULT 0,
    "status" VARCHAR(40) NOT NULL,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."SectionSchedule" (
    "id" VARCHAR(120) PRIMARY KEY,
    "sectionId" VARCHAR(120) NOT NULL REFERENCES academic."Section" ("id") ON DELETE CASCADE,
    "classroomId" VARCHAR(120) NOT NULL REFERENCES academic."Classroom" ("id"),
    "dayOfWeek" INTEGER NOT NULL,
    "startTime" VARCHAR(10) NOT NULL,
    "endTime" VARCHAR(10) NOT NULL
);

CREATE TABLE IF NOT EXISTS academic."Enrollment" (
    "id" VARCHAR(120) PRIMARY KEY,
    "studentId" VARCHAR(120) NOT NULL REFERENCES academic."Student" ("id"),
    "sectionId" VARCHAR(120) NOT NULL REFERENCES academic."Section" ("id"),
    "semesterId" VARCHAR(120) NOT NULL REFERENCES academic."Semester" ("id"),
    "status" VARCHAR(40) NOT NULL,
    "enrolledAt" TIMESTAMPTZ NOT NULL,
    "droppedAt" TIMESTAMPTZ,
    "gradeStatus" VARCHAR(40) NOT NULL,
    "finalGrade" NUMERIC(5, 2),
    "letterGrade" VARCHAR(16),
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."GradeItem" (
    "id" VARCHAR(120) PRIMARY KEY,
    "sectionId" VARCHAR(120) NOT NULL REFERENCES academic."Section" ("id") ON DELETE CASCADE,
    "name" VARCHAR(180) NOT NULL,
    "type" VARCHAR(60) NOT NULL,
    "maxScore" NUMERIC(5, 2) NOT NULL,
    "weight" NUMERIC(5, 2) NOT NULL,
    "gradedAt" TIMESTAMPTZ,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."StudentGrade" (
    "id" VARCHAR(120) PRIMARY KEY,
    "enrollmentId" VARCHAR(120) NOT NULL REFERENCES academic."Enrollment" ("id") ON DELETE CASCADE,
    "gradeItemId" VARCHAR(120) NOT NULL REFERENCES academic."GradeItem" ("id") ON DELETE CASCADE,
    "score" NUMERIC(5, 2)
);

CREATE TABLE IF NOT EXISTS engagement."Announcement" (
    "id" VARCHAR(120) PRIMARY KEY,
    "title" VARCHAR(240) NOT NULL,
    "content" TEXT NOT NULL,
    "priority" VARCHAR(40) NOT NULL,
    "targetRoles" TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    "targetYears" INTEGER[] NOT NULL DEFAULT ARRAY[]::INTEGER[],
    "isGlobal" BOOLEAN NOT NULL DEFAULT FALSE,
    "publishAt" TIMESTAMPTZ,
    "expiresAt" TIMESTAMPTZ,
    "publishedBy" VARCHAR(120),
    "semesterId" VARCHAR(120),
    "semesterName" VARCHAR(180),
    "sectionId" VARCHAR(120),
    "sectionNumber" VARCHAR(80),
    "courseCode" VARCHAR(60),
    "courseName" VARCHAR(240),
    "lecturerId" VARCHAR(120),
    "lecturerDisplayName" VARCHAR(240),
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notifications.notification (
    id VARCHAR(120) PRIMARY KEY,
    user_id VARCHAR(120) NOT NULL REFERENCES campuscore_auth."User" ("id") ON DELETE CASCADE,
    title VARCHAR(240) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    type VARCHAR(60) NOT NULL,
    link VARCHAR(500),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS academic_student_user_idx ON academic."Student" ("userId");
CREATE INDEX IF NOT EXISTS academic_section_semester_idx ON academic."Section" ("semesterId", "status");
CREATE INDEX IF NOT EXISTS academic_section_course_idx ON academic."Section" ("courseId", "status");
CREATE INDEX IF NOT EXISTS academic_enrollment_student_idx ON academic."Enrollment" ("studentId", "status");
CREATE INDEX IF NOT EXISTS academic_enrollment_section_idx ON academic."Enrollment" ("sectionId", "status");
CREATE INDEX IF NOT EXISTS engagement_announcement_publish_idx ON engagement."Announcement" ("publishAt", "expiresAt");
CREATE INDEX IF NOT EXISTS notifications_user_read_idx ON notifications.notification (user_id, is_read);
