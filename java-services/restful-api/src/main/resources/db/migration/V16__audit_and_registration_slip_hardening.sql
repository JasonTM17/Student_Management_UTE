DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM academic."GradeItem"
        WHERE "maxScore" < 0 OR "weight" < 0 OR "weight" > 100
    ) THEN
        RAISE EXCEPTION 'V16 preflight failed: invalid grade item score/weight';
    END IF;
END $$;

ALTER TABLE academic."GradeItem"
    ADD CONSTRAINT grade_item_score_non_negative CHECK ("maxScore" >= 0);
ALTER TABLE academic."GradeItem"
    ADD CONSTRAINT grade_item_weight_range CHECK ("weight" BETWEEN 0 AND 100);
ALTER TABLE academic."StudentGrade"
    ADD CONSTRAINT student_grade_score_non_negative CHECK ("score" IS NULL OR "score" >= 0);
ALTER TABLE academic."StudentGrade"
    ADD CONSTRAINT student_grade_item_unique UNIQUE ("enrollmentId", "gradeItemId");

CREATE INDEX IF NOT EXISTS registration_slip_student_time_idx
    ON academic."RegistrationSlip" ("studentId", "generatedAt" DESC);
CREATE INDEX IF NOT EXISTS enrollment_operation_key_lookup_idx
    ON academic."EnrollmentOperation" ("studentId", "idempotencyKey", "canonicalRequestHash");
