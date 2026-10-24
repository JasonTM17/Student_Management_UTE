-- Additive marker for registrations created from a curriculum default plan.
-- Dropped rows retain the marker so a later batch does not re-add them.
ALTER TABLE academic."Enrollment"
    ADD COLUMN IF NOT EXISTS "registrationSource" VARCHAR(40);
