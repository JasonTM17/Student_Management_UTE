-- H2 parity fixture for the per-student 30-credit exception workflow.
-- The H2 harness intentionally has no academic catalog foreign keys.

CREATE TABLE IF NOT EXISTS academic."CreditLimitApplication" (
    "id" VARCHAR(120) PRIMARY KEY,
    "studentId" VARCHAR(120) NOT NULL,
    "semesterId" VARCHAR(120) NOT NULL,
    "roundId" VARCHAR(120) NOT NULL,
    "requestedLimit" INTEGER NOT NULL DEFAULT 30,
    "reason" VARCHAR(1000) NOT NULL,
    "status" VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    "reviewedBy" VARCHAR(120),
    "reviewedAt" TIMESTAMP,
    "reviewerNote" VARCHAR(1000),
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "version" INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT credit_limit_application_requested_limit_valid CHECK ("requestedLimit" = 30),
    CONSTRAINT credit_limit_application_reason_valid CHECK (CHAR_LENGTH(TRIM("reason")) BETWEEN 20 AND 1000),
    CONSTRAINT credit_limit_application_status_valid CHECK ("status" IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT credit_limit_application_review_fields_valid CHECK (
        ("status" = 'PENDING' AND "reviewedBy" IS NULL AND "reviewedAt" IS NULL)
        OR ("status" IN ('APPROVED', 'REJECTED') AND "reviewedBy" IS NOT NULL AND "reviewedAt" IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS credit_limit_application_student_round_idx
    ON academic."CreditLimitApplication" ("studentId", "roundId", "status");
