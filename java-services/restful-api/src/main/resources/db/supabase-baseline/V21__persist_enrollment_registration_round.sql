-- Every enrollment must retain the exact registration round that admitted it.
-- Existing rows are backfilled only from an unambiguous round.  The legacy
-- V1-V20 demo seed predates registration rounds, so a deterministic CLOSED
-- compatibility round is created for a semester that has enrollments but no
-- round at all.  It is never OPEN and therefore cannot silently become an
-- active registration window; a semester with two or more rounds still fails
-- closed because its historical ownership cannot be inferred.
DO $$
BEGIN
    INSERT INTO academic."RegistrationRound" (
        "id", "semesterId", "status", "registrationStart", "registrationEnd",
        "addDropStart", "addDropEnd", "maxCredits", "institutionTimeZone"
    )
    SELECT
        'legacy-' || md5(s."id"),
        s."id",
        'CLOSED',
        COALESCE(s."registrationStart", s."startDate"),
        COALESCE(s."registrationEnd", s."endDate"),
        COALESCE(s."addDropStart", COALESCE(s."registrationStart", s."startDate")),
        COALESCE(s."addDropEnd", COALESCE(s."registrationEnd", s."endDate")),
        28,
        'Asia/Ho_Chi_Minh'
    FROM academic."Semester" s
    WHERE EXISTS (
        SELECT 1 FROM academic."Enrollment" e WHERE e."semesterId" = s."id"
    )
      AND NOT EXISTS (
        SELECT 1 FROM academic."RegistrationRound" r WHERE r."semesterId" = s."id"
    )
      AND COALESCE(s."registrationStart", s."startDate") IS NOT NULL
      AND COALESCE(s."registrationEnd", s."endDate") IS NOT NULL
      AND COALESCE(s."addDropStart", COALESCE(s."registrationStart", s."startDate")) IS NOT NULL
      AND COALESCE(s."addDropEnd", COALESCE(s."registrationEnd", s."endDate")) IS NOT NULL
      AND COALESCE(s."registrationStart", s."startDate")
          < COALESCE(s."registrationEnd", s."endDate")
      AND COALESCE(s."addDropStart", COALESCE(s."registrationStart", s."startDate"))
          < COALESCE(s."addDropEnd", COALESCE(s."registrationEnd", s."endDate"))
    ON CONFLICT ("id") DO NOTHING;

    IF EXISTS (
        SELECT 1
        FROM academic."Enrollment" e
        LEFT JOIN academic."RegistrationRound" r ON r."semesterId" = e."semesterId"
        GROUP BY e."id"
        HAVING COUNT(r."id") <> 1
    ) THEN
        RAISE EXCEPTION 'V21 preflight failed: enrollment round ownership is missing or ambiguous';
    END IF;
END $$;

ALTER TABLE academic."Enrollment"
    ADD COLUMN IF NOT EXISTS "roundId" VARCHAR(120);

UPDATE academic."Enrollment" e
SET "roundId" = r."id"
FROM academic."RegistrationRound" r
WHERE r."semesterId" = e."semesterId"
  AND e."roundId" IS NULL;

ALTER TABLE academic."Enrollment"
    ALTER COLUMN "roundId" SET NOT NULL;

ALTER TABLE academic."RegistrationRound"
    ADD CONSTRAINT registration_round_id_semester_unique UNIQUE ("id", "semesterId");

ALTER TABLE academic."Enrollment"
    ADD CONSTRAINT enrollment_round_semester_fk
    FOREIGN KEY ("roundId", "semesterId")
    REFERENCES academic."RegistrationRound" ("id", "semesterId");

CREATE INDEX IF NOT EXISTS enrollment_student_round_status_idx
    ON academic."Enrollment" ("studentId", "roundId", "status");
