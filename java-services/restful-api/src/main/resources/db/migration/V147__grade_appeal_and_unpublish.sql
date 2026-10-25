-- Student grade appeals and administrative unpublishing.
-- V147 adds a per-enrollment publication instant because Enrollment.updatedAt
-- is also touched by draft edits and is not an appeal-window authority.

ALTER TABLE academic."Enrollment"
    ADD COLUMN IF NOT EXISTS "gradePublishedAt" TIMESTAMPTZ;

-- Existing official grades receive a one-time grace window beginning when
-- this migration is applied. Restricting the update to NULL keeps a retry from
-- moving the deadline forward.
UPDATE academic."Enrollment"
   SET "gradePublishedAt" = CURRENT_TIMESTAMP
 WHERE "gradeStatus" IN ('PUBLISHED', 'APPEALED')
   AND "gradePublishedAt" IS NULL;

CREATE TABLE IF NOT EXISTS academic."GradeAppeal" (
    "id" VARCHAR(120) PRIMARY KEY,
    "enrollmentId" VARCHAR(120) NOT NULL
        REFERENCES academic."Enrollment" ("id") ON DELETE CASCADE,
    "status" VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    "reason" VARCHAR(1000) NOT NULL,
    "resolutionReason" VARCHAR(1000),
    "reviewedBy" VARCHAR(120),
    "reviewedAt" TIMESTAMPTZ,
    "beforeFinalGrade" NUMERIC(5, 2),
    "beforeLetterGrade" VARCHAR(16),
    "afterFinalGrade" NUMERIC(5, 2),
    "afterLetterGrade" VARCHAR(16),
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "version" INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT grade_appeal_reason_valid
        CHECK (char_length(btrim("reason")) BETWEEN 20 AND 1000),
    CONSTRAINT grade_appeal_resolution_reason_valid
        CHECK ("resolutionReason" IS NULL OR char_length(btrim("resolutionReason")) BETWEEN 20 AND 1000),
    CONSTRAINT grade_appeal_status_valid
        CHECK ("status" IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT grade_appeal_review_fields_valid
        CHECK (
            ("status" IN ('PENDING', 'CANCELLED') AND "reviewedBy" IS NULL AND "reviewedAt" IS NULL)
            OR ("status" IN ('ACCEPTED', 'REJECTED')
                AND "reviewedBy" IS NOT NULL AND "reviewedAt" IS NOT NULL
                AND "resolutionReason" IS NOT NULL)
        ),
    CONSTRAINT grade_appeal_accepted_snapshot_valid
        CHECK (
            "status" <> 'ACCEPTED'
            OR ("beforeFinalGrade" IS NOT NULL AND "beforeLetterGrade" IS NOT NULL
                AND "afterFinalGrade" IS NOT NULL AND "afterLetterGrade" IS NOT NULL)
        )
);

CREATE INDEX IF NOT EXISTS grade_appeal_status_created_idx
    ON academic."GradeAppeal" ("status", "createdAt", "id");

CREATE INDEX IF NOT EXISTS grade_appeal_enrollment_status_idx
    ON academic."GradeAppeal" ("enrollmentId", "status");

-- A single pending appeal per enrollment is a final database race guard.
-- The service also retains every terminal row and enforces the one-appeal-ever
-- rule while holding the owning Student row lock.
CREATE UNIQUE INDEX IF NOT EXISTS grade_appeal_pending_enrollment_uq
    ON academic."GradeAppeal" ("enrollmentId")
    WHERE "status" = 'PENDING';
