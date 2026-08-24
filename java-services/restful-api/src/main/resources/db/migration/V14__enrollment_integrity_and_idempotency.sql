DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM academic."Enrollment" e
        JOIN academic."Section" s ON s."id" = e."sectionId"
        WHERE e."semesterId" <> s."semesterId"
    ) THEN
        RAISE EXCEPTION 'V14 preflight failed: enrollment/section semester mismatch';
    END IF;
END $$;

ALTER TABLE academic."Section" ADD COLUMN IF NOT EXISTS "version" BIGINT NOT NULL DEFAULT 0;
ALTER TABLE academic."Enrollment" ADD COLUMN IF NOT EXISTS "version" BIGINT NOT NULL DEFAULT 0;
ALTER TABLE academic."Course" ADD COLUMN IF NOT EXISTS "version" BIGINT NOT NULL DEFAULT 0;

ALTER TABLE academic."Section"
    ADD CONSTRAINT section_capacity_non_negative CHECK ("capacity" >= 0);
ALTER TABLE academic."Section"
    ADD CONSTRAINT section_enrolled_count_valid CHECK ("enrolledCount" >= 0 AND "enrolledCount" <= "capacity");
ALTER TABLE academic."Enrollment"
    ADD CONSTRAINT enrollment_semester_required CHECK (length(trim("semesterId")) > 0);

CREATE UNIQUE INDEX IF NOT EXISTS enrollment_active_student_section_uq
    ON academic."Enrollment" ("studentId", "sectionId")
    WHERE lower("status") IN ('active', 'enrolled');

CREATE INDEX IF NOT EXISTS enrollment_student_semester_status_idx
    ON academic."Enrollment" ("studentId", "semesterId", "status");
