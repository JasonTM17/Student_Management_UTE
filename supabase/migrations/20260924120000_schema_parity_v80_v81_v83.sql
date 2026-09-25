-- Migration 20260924120000_schema_parity_v80_v81_v83.sql
-- Closes the drift between the Flyway chain (the runtime schema authority,
-- see java-services/restful-api/src/main/resources/db/migration/README.md)
-- and this Supabase set. Three guards existed only on the Flyway side:
--   * V80: school-wide unique course code (round-9 fix for SE401-404
--     being seeded for two different courses) plus the SE421-424 renumber
--   * V81: referential integrity on thesis_council_member.lecturer_id
--   * V14: the L3 partial unique indexes that make double enrollment
--     impossible even when the application layer is bypassed
-- plus V83's hot-path FK indexes. All statements are idempotent.

-- 1. V80 parity: renumber any surviving duplicate SE40x codes, then enforce
--    the unique code. The UPDATE is a no-op on a database that already
--    applied V80's data fix; the index creation is what matters here.
UPDATE academic."Course" c
SET "code" = 'SE42' || (SUBSTRING(c."code" FROM 5))
WHERE c."code" IN ('SE401', 'SE402', 'SE403', 'SE404')
  AND EXISTS (
      SELECT 1 FROM academic."Course" other
      WHERE other."code" = c."code" AND other."id" <> c."id"
  );

CREATE UNIQUE INDEX IF NOT EXISTS ux_course_code
    ON academic."Course" ("code");

-- 2. V81 parity: council membership must reference a real lecturer profile.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'thesis_council_member_lecturer_fk'
    ) THEN
        ALTER TABLE thesis.thesis_council_member
            ADD CONSTRAINT thesis_council_member_lecturer_fk
            FOREIGN KEY (lecturer_id)
            REFERENCES academic."Lecturer" ("id")
            ON DELETE RESTRICT;
    END IF;
END $$;

-- 3. V14 parity: the L3 partial unique indexes.
CREATE UNIQUE INDEX IF NOT EXISTS academic_enrollment_active_student_section_uq
    ON academic."Enrollment" ("studentId", "sectionId")
    WHERE "status" IN ('ENROLLED', 'PENDING', 'CONFIRMED');

CREATE UNIQUE INDEX IF NOT EXISTS academic_enrollment_active_student_course_semester_uq
    ON academic."Enrollment" ("studentId", "courseId", "semesterId")
    WHERE "status" IN ('ENROLLED', 'PENDING', 'CONFIRMED');

-- 4. V83 parity: hot-path FK indexes.
CREATE INDEX IF NOT EXISTS academic_section_lecturer_idx
    ON academic."Section" ("lecturerId");

CREATE INDEX IF NOT EXISTS academic_course_department_idx
    ON academic."Course" ("departmentId");

CREATE INDEX IF NOT EXISTS academic_course_semester_idx
    ON academic."Course" ("semesterId");

CREATE INDEX IF NOT EXISTS engagement_announcement_category_idx
    ON engagement."Announcement" ("categoryId");

CREATE INDEX IF NOT EXISTS thesis_council_member_lecturer_idx
    ON thesis.thesis_council_member ("lecturer_id");
