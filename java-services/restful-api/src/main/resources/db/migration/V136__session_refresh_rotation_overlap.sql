-- V136: one-generation rotation overlap for browser refresh sessions.
--
-- Refresh rotation is hash-keyed: the row stores SHA-256("refreshToken") and
-- every exchange rewrites it. Same-origin documents (tabs, iframes) share one
-- cookie jar, so a document can still present the previous token after a
-- sibling rotated it — typically because the rotating response was lost
-- mid-flight (navigation, transport timeout, network cut). Treating that
-- replay as theft revoked the whole session family and signed the user out.
--
-- "previousRefreshToken" keeps the hash the current token replaced and
-- "rotatedAt" stamps when it did, letting refresh recognize an
-- immediately-previous token inside a short overlap window and recover it
-- instead of revoking the family. Replays older than the window — and every
-- hash that never owned the row — still revoke, so stolen-token detection is
-- unchanged.

ALTER TABLE campuscore_auth."Session"
    ADD COLUMN IF NOT EXISTS "previousRefreshToken" VARCHAR(200);

ALTER TABLE campuscore_auth."Session"
    ADD COLUMN IF NOT EXISTS "rotatedAt" TIMESTAMP;

CREATE INDEX IF NOT EXISTS campuscore_auth_session_prev_refresh_idx
    ON campuscore_auth."Session" ("previousRefreshToken");
