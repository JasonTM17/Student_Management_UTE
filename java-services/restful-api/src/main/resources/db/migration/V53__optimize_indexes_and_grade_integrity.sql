-- V53__optimize_indexes_and_grade_integrity.sql
-- Purpose: Optimize database indexes for StudentGrade and ConductActivity
-- Prevent duplicate grade components and eliminate Sequential Scans on grade publication and conduct lookups

-- 1. Deduplicate any pre-existing duplicates in academic."StudentGrade" before creating unique index
DELETE FROM academic."StudentGrade" a
WHERE a.ctid <> (
    SELECT min(b.ctid)
    FROM academic."StudentGrade" b
    WHERE a."enrollmentId" = b."enrollmentId"
      AND a."gradeItemId" = b."gradeItemId"
);

-- 2. Grade uniqueness and lookup indexes
CREATE UNIQUE INDEX IF NOT EXISTS academic_student_grade_enrollment_item_uq
    ON academic."StudentGrade" ("enrollmentId", "gradeItemId");

CREATE INDEX IF NOT EXISTS academic_student_grade_enrollment_idx
    ON academic."StudentGrade" ("enrollmentId");

-- 3. Conduct activity student-semester composite index
CREATE INDEX IF NOT EXISTS academic_conduct_activity_student_semester_idx
    ON academic.conduct_activity (student_id, semester_id);
