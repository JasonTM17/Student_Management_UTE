-- H2 twin of production V85 (see src/main/resources/db/migration/V85__two_factor_email_login.sql):
-- opt-in email OTP two-factor authentication at login.

ALTER TABLE campuscore_auth."User"
    ADD COLUMN IF NOT EXISTS "twoFactorEnabled" BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS campuscore_auth."TwoFactorChallenge" (
    "id" VARCHAR(120) PRIMARY KEY,
    "userId" VARCHAR(120) NOT NULL REFERENCES campuscore_auth."User" ("id") ON DELETE CASCADE,
    "purpose" VARCHAR(16) NOT NULL CHECK ("purpose" IN ('LOGIN', 'ENABLE')),
    "codeHash" VARCHAR(64) NOT NULL,
    "expiresAt" TIMESTAMP WITH TIME ZONE NOT NULL,
    "consumedAt" TIMESTAMP WITH TIME ZONE,
    "attempts" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS campuscore_auth_two_factor_challenge_user_idx
    ON campuscore_auth."TwoFactorChallenge" ("userId", "purpose", "consumedAt");
