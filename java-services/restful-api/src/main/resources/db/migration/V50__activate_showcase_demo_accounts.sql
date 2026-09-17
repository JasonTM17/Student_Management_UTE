-- Reactivate showcase demo accounts so evaluators and students can test the portal via the "Điền nhanh" button on the login page.
UPDATE campuscore_auth."User"
SET "status" = 'ACTIVE',
    "failedLoginAttempts" = 0,
    "lockedUntil" = NULL,
    "password" = '$2a$10$raV9MB3Qmj1Rbu2Rmo1vNup7VsC2OM3AqmcTcTLzbNyMyI4r2rJBe',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "email" IN ('student@campuscore.edu', 'lecturer@campuscore.edu');

UPDATE campuscore_auth."User"
SET "status" = 'ACTIVE',
    "failedLoginAttempts" = 0,
    "lockedUntil" = NULL,
    "password" = '$2a$10$w/C/Sws8BTLaumspjtjD6.JTq5za1EkN8imIyuUiblazqRxiRYcmG',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "email" = 'admin@campuscore.edu';
