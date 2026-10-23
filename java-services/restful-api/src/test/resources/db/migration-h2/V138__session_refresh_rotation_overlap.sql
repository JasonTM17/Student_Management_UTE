-- H2 mirror of V138: one-generation rotation overlap columns on Session.
-- Covers only the lost-response ordering — see the production V138 file for
-- the full contract, including the uncovered delayed-duplicate ordering and
-- the deferred stolen-token detection.

ALTER TABLE campuscore_auth."Session"
    ADD COLUMN IF NOT EXISTS "previousRefreshToken" VARCHAR(200);

ALTER TABLE campuscore_auth."Session"
    ADD COLUMN IF NOT EXISTS "rotatedAt" TIMESTAMP;

CREATE INDEX IF NOT EXISTS campuscore_auth_session_prev_refresh_idx
    ON campuscore_auth."Session" ("previousRefreshToken");
