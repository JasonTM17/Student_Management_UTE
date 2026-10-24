-- H2 parity fixture for the approved course-withdrawal (grade W) workflow.
-- The H2 harness intentionally has no academic catalog foreign keys.

CREATE TABLE IF NOT EXISTS academic."WithdrawalApplication" (
    "id" VARCHAR(120) PRIMARY KEY,
    "studentId" VARCHAR(120) NOT NULL,
    "enrollmentId" VARCHAR(120) NOT NULL,
    "semesterId" VARCHAR(120) NOT NULL,
    "reason" VARCHAR(1000) NOT NULL,
    "status" VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    "reviewedBy" VARCHAR(120),
    "reviewedAt" TIMESTAMP,
    "reviewerNote" VARCHAR(1000),
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "version" INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT withdrawal_application_reason_valid CHECK (CHAR_LENGTH(TRIM("reason")) BETWEEN 20 AND 1000),
    CONSTRAINT withdrawal_application_status_valid CHECK ("status" IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT withdrawal_application_review_fields_valid CHECK (
        ("status" IN ('PENDING', 'CANCELLED') AND "reviewedBy" IS NULL AND "reviewedAt" IS NULL)
        OR ("status" IN ('APPROVED', 'REJECTED') AND "reviewedBy" IS NOT NULL AND "reviewedAt" IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS withdrawal_application_student_idx
    ON academic."WithdrawalApplication" ("studentId", "status");

CREATE INDEX IF NOT EXISTS withdrawal_application_enrollment_idx
    ON academic."WithdrawalApplication" ("enrollmentId", "status");
