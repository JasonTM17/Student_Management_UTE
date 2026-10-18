-- V125__revoke_reconciled_demo_sessions.sql
-- V124 replaced the password hash on the three reconciled demo role accounts
-- (admin002@campuscore.demo, truongkhoa@campuscore.demo,
-- truongbomon@campuscore.demo) without bumping credentialVersion, clearing
-- refresh sessions, or consuming pending LOGIN two-factor challenges. Mirror
-- AuthUserRepository.resetPassword + AuthLoginService session revocation so any
-- pre-reconciliation access token, refresh session, or pending login challenge
-- is invalidated atomically.

UPDATE campuscore_auth."User"
SET "credentialVersion" = "credentialVersion" + 1,
    "passwordChangedAt" = CURRENT_TIMESTAMP,
    "refreshToken" = NULL,
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "email" IN (
    'admin002@campuscore.demo',
    'truongkhoa@campuscore.demo',
    'truongbomon@campuscore.demo'
);

DELETE FROM campuscore_auth."Session"
WHERE "userId" IN (
    SELECT "id" FROM campuscore_auth."User"
    WHERE "email" IN (
        'admin002@campuscore.demo',
        'truongkhoa@campuscore.demo',
        'truongbomon@campuscore.demo'
    )
);

UPDATE campuscore_auth."TwoFactorChallenge"
SET "consumedAt" = CURRENT_TIMESTAMP
WHERE "purpose" = 'LOGIN'
  AND "consumedAt" IS NULL
  AND "userId" IN (
      SELECT "id" FROM campuscore_auth."User"
      WHERE "email" IN (
          'admin002@campuscore.demo',
          'truongkhoa@campuscore.demo',
          'truongbomon@campuscore.demo'
      )
  );
