-- H2 twin of V48__lock_seeded_demo_accounts.sql (kept byte-for-byte in
-- statement shape so the test schema matches the production migration).
UPDATE campuscore_auth."User"
SET "status" = 'LOCKED',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "status" = 'ACTIVE'
  AND (
        "email" LIKE '%@campuscore.demo'
        OR "email" IN ('student@campuscore.edu', 'lecturer@campuscore.edu', 'admin@campuscore.edu')
      );
