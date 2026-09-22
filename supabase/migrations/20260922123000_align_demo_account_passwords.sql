-- Supabase twin of V77__align_demo_account_passwords.sql.
UPDATE campuscore_auth."User"
SET "password" = '$2a$10$raV9MB3Qmj1Rbu2Rmo1vNup7VsC2OM3AqmcTcTLzbNyMyI4r2rJBe',
    "failedLoginAttempts" = 0,
    "lockedUntil" = NULL,
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "email" IN (
    'admin@campuscore.edu',
    'admin002@campuscore.demo',
    'lecturer002@campuscore.demo',
    'lecturer003@campuscore.demo',
    'lecturer004@campuscore.demo',
    'lecturer005@campuscore.demo',
    'lecturer006@campuscore.demo',
    'lecturer007@campuscore.demo',
    'lecturer008@campuscore.demo',
    'lecturer009@campuscore.demo',
    'lecturer010@campuscore.demo',
    'lecturer011@campuscore.demo',
    'lecturer012@campuscore.demo'
);
