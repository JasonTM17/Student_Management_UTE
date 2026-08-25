CREATE TABLE IF NOT EXISTS auth."AuthChallenge" (
    "id" VARCHAR(120) PRIMARY KEY,
    "userId" VARCHAR(120) NOT NULL,
    "purpose" VARCHAR(40) NOT NULL,
    "tokenHash" VARCHAR(64) NOT NULL,
    "expiresAt" TIMESTAMP NOT NULL,
    "consumedAt" TIMESTAMP,
    "attemptCount" INTEGER NOT NULL DEFAULT 0,
    "lastSentAt" TIMESTAMP NOT NULL,
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT auth_challenge_purpose_valid CHECK ("purpose" IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    CONSTRAINT auth_challenge_attempts_valid CHECK ("attemptCount" >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS auth_challenge_token_hash_uq
    ON auth."AuthChallenge" ("tokenHash");
CREATE INDEX IF NOT EXISTS auth_challenge_user_purpose_idx
    ON auth."AuthChallenge" ("userId", "purpose", "createdAt");

CREATE TABLE IF NOT EXISTS auth."AuthRateLimitBucket" (
    "scope" VARCHAR(80) NOT NULL,
    "bucketKeyHash" VARCHAR(64) NOT NULL,
    "windowStart" TIMESTAMP NOT NULL,
    "requestCount" INTEGER NOT NULL DEFAULT 0,
    "updatedAt" TIMESTAMP NOT NULL,
    PRIMARY KEY ("scope", "bucketKeyHash", "windowStart"),
    CONSTRAINT auth_rate_limit_count_valid CHECK ("requestCount" >= 0)
);

CREATE INDEX IF NOT EXISTS auth_rate_limit_updated_idx
    ON auth."AuthRateLimitBucket" ("updatedAt");

-- Accounts created before lifecycle enforcement are trusted demo/admin-managed
-- accounts. New self-registration explicitly uses PENDING_VERIFICATION.
UPDATE auth."User"
SET "emailVerified" = TRUE,
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "status" = 'ACTIVE' AND "emailVerified" = FALSE;
