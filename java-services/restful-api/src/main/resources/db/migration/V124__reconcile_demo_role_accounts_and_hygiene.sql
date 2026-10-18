-- V124__reconcile_demo_role_accounts_and_hygiene.sql
-- Reconciles the runbook-documented demo role accounts and role hygiene:
--   1) Aligns admin002@campuscore.demo (Four-Eyes second approver),
--      truongkhoa@campuscore.demo (faculty head), and
--      truongbomon@campuscore.demo (department head) on the single published
--      demo password (password123, same bcrypt hash as V50/V77/V116) with clean
--      lockout/rotation/2FA flags so every documented role works out of the box
--      when DEMO_ACCOUNTS_ENABLED=true and is locked by DemoAccountGate when
--      DEMO_ACCOUNTS_ENABLED=false.
--   2) Ensures truongkhoa-user-001 and truongkhoa-user-002 retain their active
--      Lecturer profiles in department-demo.
--   3) Re-applies the V89 R1 office/faculty-head separation so administrative
--      office accounts (ADMIN / SUPER_ADMIN) never carry TRUONG_KHOA.

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

UPDATE academic."Lecturer"
SET "isActive" = TRUE,
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "userId" IN ('truongkhoa-user-001', 'truongkhoa-user-002')
  AND "isActive" IS DISTINCT FROM TRUE;

DELETE FROM campuscore_auth."UserRole"
WHERE "roleId" = (SELECT "id" FROM campuscore_auth."Role" WHERE "name" = 'TRUONG_KHOA')
  AND "userId" IN (
      SELECT held."userId"
      FROM campuscore_auth."UserRole" held
      JOIN campuscore_auth."Role" office ON office."id" = held."roleId"
      WHERE office."name" IN ('ADMIN', 'SUPER_ADMIN')
  );
