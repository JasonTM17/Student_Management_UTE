-- H2 mirror for appeal persistence tests. H2's migration fixture omits the
-- PostgreSQL partial index; a unique enrollment index enforces the stronger
-- one-appeal-ever rule used by the service in this engine.

ALTER TABLE academic."Enrollment"
    ADD COLUMN IF NOT EXISTS "gradePublishedAt" TIMESTAMP WITH TIME ZONE;

UPDATE academic."Enrollment"
   SET "gradePublishedAt" = CURRENT_TIMESTAMP
 WHERE "gradeStatus" IN ('PUBLISHED', 'APPEALED')
   AND "gradePublishedAt" IS NULL;

CREATE TABLE IF NOT EXISTS academic."GradeAppeal" (
    "id" VARCHAR(120) PRIMARY KEY,
    "enrollmentId" VARCHAR(120) NOT NULL,
    "status" VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    "reason" VARCHAR(1000) NOT NULL,
    "resolutionReason" VARCHAR(1000),
    "reviewedBy" VARCHAR(120),
    "reviewedAt" TIMESTAMP WITH TIME ZONE,
    "beforeFinalGrade" NUMERIC(5, 2),
    "beforeLetterGrade" VARCHAR(16),
    "afterFinalGrade" NUMERIC(5, 2),
    "afterLetterGrade" VARCHAR(16),
    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "version" INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT grade_appeal_reason_valid
        CHECK (CHAR_LENGTH(TRIM("reason")) BETWEEN 20 AND 1000),
    CONSTRAINT grade_appeal_resolution_reason_valid
        CHECK ("resolutionReason" IS NULL OR CHAR_LENGTH(TRIM("resolutionReason")) BETWEEN 20 AND 1000),
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

CREATE UNIQUE INDEX IF NOT EXISTS grade_appeal_enrollment_uq
    ON academic."GradeAppeal" ("enrollmentId");

CREATE INDEX IF NOT EXISTS grade_appeal_status_created_idx
    ON academic."GradeAppeal" ("status", "createdAt", "id");
