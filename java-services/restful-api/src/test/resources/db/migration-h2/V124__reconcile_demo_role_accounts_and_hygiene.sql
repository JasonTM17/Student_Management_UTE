-- H2 twin of production V124__reconcile_demo_role_accounts_and_hygiene.sql.
UPDATE campuscore_auth."User"
SET "password" = '$2a$10$raV9MB3Qmj1Rbu2Rmo1vNup7VsC2OM3AqmcTcTLzbNyMyI4r2rJBe',
    "status" = 'ACTIVE',
    "failedLoginAttempts" = 0,
    "lockedUntil" = NULL,
    "mustChangePassword" = FALSE,
    "twoFactorEnabled" = FALSE,
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "email" IN (
    'admin002@campuscore.demo',
    'truongkhoa@campuscore.demo',
    'truongbomon@campuscore.demo'
);

DELETE FROM campuscore_auth."UserRole"
WHERE "roleId" = (SELECT "id" FROM campuscore_auth."Role" WHERE "name" = 'TRUONG_KHOA')
  AND "userId" IN (
      SELECT held."userId"
      FROM campuscore_auth."UserRole" held
      JOIN campuscore_auth."Role" office ON office."id" = held."roleId"
      WHERE office."name" IN ('ADMIN', 'SUPER_ADMIN')
  );
