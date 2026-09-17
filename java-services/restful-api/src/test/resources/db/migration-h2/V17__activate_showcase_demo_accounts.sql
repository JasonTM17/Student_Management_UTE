-- H2 twin of V50__activate_showcase_demo_accounts.sql
UPDATE "campuscore_auth"."User"
SET "status" = 'ACTIVE',
    "failedLoginAttempts" = 0,
    "lockedUntil" = NULL,
    "password" = '$2a$10$raV9MB3Qmj1Rbu2Rmo1vNup7VsC2OM3AqmcTcTLzbNyMyI4r2rJBe',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "email" IN ('student@campuscore.edu', 'lecturer@campuscore.edu');

UPDATE "campuscore_auth"."User"
SET "status" = 'ACTIVE',
    "failedLoginAttempts" = 0,
    "lockedUntil" = NULL,
    "password" = '$2a$10$w/C/Sws8BTLaumspjtjD6.JTq5za1EkN8imIyuUiblazqRxiRYcmG',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "email" = 'admin@campuscore.edu';
