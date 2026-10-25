-- H2 parity fixture for faculty-targeted announcements (R14). Same additive,
-- idempotent column as the PostgreSQL V149; the H2 harness declares arrays
-- with the VARCHAR ARRAY spelling its tests already use.

ALTER TABLE engagement."Announcement"
    ADD COLUMN IF NOT EXISTS "targetFaculties" VARCHAR ARRAY NOT NULL DEFAULT ARRAY[];
