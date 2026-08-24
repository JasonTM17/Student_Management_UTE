-- V14 protected the two legacy active states.  The registration contract also
-- treats pending/confirmed rows as capacity-bearing, so extend the invariant
-- without rewriting the existing index or any historical data.
DO $$
BEGIN
    IF EXISTS (
        SELECT "studentId", "sectionId"
        FROM academic."Enrollment"
        WHERE lower("status") IN ('active', 'enrolled', 'pending', 'confirmed')
        GROUP BY "studentId", "sectionId"
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V18 preflight failed: duplicate active academic.Enrollment rows';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS enrollment_active_student_section_all_status_uq
    ON academic."Enrollment" ("studentId", "sectionId")
    WHERE lower("status") IN ('active', 'enrolled', 'pending', 'confirmed');
