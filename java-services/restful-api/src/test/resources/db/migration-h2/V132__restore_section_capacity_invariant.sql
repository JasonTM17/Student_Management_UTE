-- H2 twin for V132. The H2 Section stub (V117) only carries id/lecturerId, so
-- the mirror adds the columns the repair reads before running the identical
-- UPDATE; portable SQL, repairs nothing on the empty fixture set but keeps the
-- migration exercised in tests.
ALTER TABLE academic."Section" ADD COLUMN IF NOT EXISTS "capacity" INT;
ALTER TABLE academic."Section" ADD COLUMN IF NOT EXISTS "enrolledCount" INT;
ALTER TABLE academic."Section" ADD COLUMN IF NOT EXISTS "updatedAt" TIMESTAMP;

UPDATE academic."Section"
SET "capacity" = ((("enrolledCount" + 24) / 25) * 25),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "enrolledCount" > "capacity";
