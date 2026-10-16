ALTER TABLE campuscore_auth."User"
    ADD COLUMN "credentialVersion" BIGINT NOT NULL DEFAULT 0
    CHECK ("credentialVersion" >= 0);
