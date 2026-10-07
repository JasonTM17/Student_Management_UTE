-- Data repair: 56 seeded sections carried "enrolledCount" counters that did
-- not match the true active enrollment count (stored ~= 2x actual on the
-- section-auto-* / *-demo seeds). The seat guard compares enrolledCount <
-- capacity, so inflated counters hide real seats. Applies the same
-- reconciliation UPDATE EnrolledCountReconcileJob runs in repair mode;
-- idempotent (WHERE clause limits it to genuinely drifted rows) and a no-op
-- wherever counters are already correct.

UPDATE academic."Section" section
SET "enrolledCount" = (
        SELECT COUNT(*) FROM academic."Enrollment" enrollment
        WHERE enrollment."sectionId" = section."id"
          AND enrollment."status" IN ('ENROLLED', 'PENDING', 'CONFIRMED')),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE section."enrolledCount" <> (
        SELECT COUNT(*) FROM academic."Enrollment" enrollment
        WHERE enrollment."sectionId" = section."id"
          AND enrollment."status" IN ('ENROLLED', 'PENDING', 'CONFIRMED'));
