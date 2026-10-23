-- V138: one-generation rotation overlap for browser refresh sessions.
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
-- instead of revoking the family.
--
-- Scope of the exception — the overlap covers ONLY the lost-response
-- ordering. It does not cover the delayed-duplicate ordering: when two
-- requests carry the same T0, the second-processed request rotates T0->T1
-- and delivers T1 to the jar, after which the delayed T0 recovers and
-- overwrites the row with T2 — the delivered T1 is then neither current
-- nor previous, so its next refresh still revokes the family.
-- Stolen-token detection is deferred, not removed: inside the window a
-- replayed previous token yields the replayer a fresh pair and evicts the
-- legitimate holder, who is signed out at their next refresh. Replays
-- older than the window — and every hash that never owned the row — still
-- revoke immediately.

ALTER TABLE campuscore_auth."Session"
    ADD COLUMN IF NOT EXISTS "previousRefreshToken" VARCHAR(200);

ALTER TABLE campuscore_auth."Session"
    ADD COLUMN IF NOT EXISTS "rotatedAt" TIMESTAMP;

CREATE INDEX IF NOT EXISTS campuscore_auth_session_prev_refresh_idx
    ON campuscore_auth."Session" ("previousRefreshToken");
