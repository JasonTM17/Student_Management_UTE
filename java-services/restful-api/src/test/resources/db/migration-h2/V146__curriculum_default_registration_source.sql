ALTER TABLE academic."Enrollment"
    ADD COLUMN IF NOT EXISTS "registrationSource" VARCHAR(40);
