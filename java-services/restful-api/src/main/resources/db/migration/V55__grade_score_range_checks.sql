-- Grade scores are bounded by definition: a component score and an enrolled
-- final grade live on the official 0-10 scale. The API validates this, but a
-- direct write or a regression could otherwise persist out-of-range numbers
-- that every downstream average then trusts.
ALTER TABLE academic."StudentGrade"
    ADD CONSTRAINT academic_student_grade_score_range
    CHECK ("score" IS NULL OR "score" BETWEEN 0 AND 10);

ALTER TABLE academic."Enrollment"
    ADD CONSTRAINT academic_enrollment_final_grade_range
    CHECK ("finalGrade" IS NULL OR "finalGrade" BETWEEN 0 AND 10);
