-- A credential revision avoids timestamp/JWT-second ambiguity during rotation.
-- Unaffected legacy access tokens remain revision zero until a password changes.
ALTER TABLE campuscore_auth."User"
    ADD COLUMN "credentialVersion" BIGINT NOT NULL DEFAULT 0
    CHECK ("credentialVersion" >= 0);
