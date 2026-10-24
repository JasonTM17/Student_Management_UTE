-- Approved course withdrawal (grade W). A student may request withdrawal for
-- an enrollment of an open regular semester inside the first
-- registration.withdrawal.window.days days; Academic Affairs approves or
-- rejects. The Enrollment status/letterGrade columns are unconstrained
-- VARCHARs, so 'WITHDRAWN'/'W' need no ALTER here. Forward-only; V143
-- (eligibility seed) and the H2-only V144 test mirror belong to release 2,
-- so this file must not depend on either.

CREATE TABLE IF NOT EXISTS academic."WithdrawalApplication" (
    "id" VARCHAR(120) PRIMARY KEY,
    "studentId" VARCHAR(120) NOT NULL REFERENCES academic."Student" ("id") ON DELETE CASCADE,
    "enrollmentId" VARCHAR(120) NOT NULL REFERENCES academic."Enrollment" ("id") ON DELETE CASCADE,
    "semesterId" VARCHAR(120) NOT NULL REFERENCES academic."Semester" ("id") ON DELETE CASCADE,
    "reason" VARCHAR(1000) NOT NULL,
    "status" VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    "reviewedBy" VARCHAR(120),
    "reviewedAt" TIMESTAMPTZ,
    "reviewerNote" VARCHAR(1000),
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "version" INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT withdrawal_application_reason_valid CHECK (char_length(btrim("reason")) BETWEEN 20 AND 1000),
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

-- One pending application per enrollment. Cancelled and rejected history is
-- kept, so the guard is a partial unique index rather than a plain one.
CREATE UNIQUE INDEX IF NOT EXISTS withdrawal_application_pending_enrollment_uq
    ON academic."WithdrawalApplication" ("enrollmentId")
    WHERE "status" = 'PENDING';
