-- H2 twin of V55__grade_score_range_checks.sql.
-- The runtime H2 chain does not create the academic grade tables (manual
-- fixtures own them), so the mirror creates them IF NOT EXISTS with the
-- fixture-compatible shape before adding the same range constraints.
CREATE TABLE IF NOT EXISTS academic."StudentGrade" (
    "id" VARCHAR(120) PRIMARY KEY,
    "enrollmentId" VARCHAR(120) NOT NULL,
    "gradeItemId" VARCHAR(120) NOT NULL,
    "score" DECIMAL(5, 2),
    "gradedAt" TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."Enrollment" (
    "id" VARCHAR(120) PRIMARY KEY,
    "studentId" VARCHAR(120) NOT NULL,
    "sectionId" VARCHAR(120) NOT NULL,
    "semesterId" VARCHAR(120) NOT NULL,
    "status" VARCHAR(40) NOT NULL,
    "enrolledAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "droppedAt" TIMESTAMP,
    "gradeStatus" VARCHAR(40) NOT NULL DEFAULT 'NOT_GRADED',
    "finalGrade" DECIMAL(5, 2),
    "letterGrade" VARCHAR(10),
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE academic."StudentGrade"
    ADD CONSTRAINT academic_student_grade_score_range
    CHECK ("score" IS NULL OR "score" BETWEEN 0 AND 10);

ALTER TABLE academic."Enrollment"
    ADD CONSTRAINT academic_enrollment_final_grade_range
    CHECK ("finalGrade" IS NULL OR "finalGrade" BETWEEN 0 AND 10);
