-- H2 mirror of V136: one-generation rotation overlap columns on Session.

ALTER TABLE campuscore_auth."Session"
    ADD COLUMN IF NOT EXISTS "previousRefreshToken" VARCHAR(200);

ALTER TABLE campuscore_auth."Session"
    ADD COLUMN IF NOT EXISTS "rotatedAt" TIMESTAMP;

CREATE INDEX IF NOT EXISTS campuscore_auth_session_prev_refresh_idx
    ON campuscore_auth."Session" ("previousRefreshToken");
