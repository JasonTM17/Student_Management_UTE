-- Seeded demo accounts must never be usable as-is outside a local demo:
-- their credentials are printed in the README, so any deployment that ran
-- the seed migrations (including production) would expose working student,
-- lecturer and admin logins. Lock them at first migration after V47; local
-- demo environments re-activate selected accounts with explicit SQL (see
-- README "Local demo accounts"). Never edit this file once applied — a
-- corrective change is a new forward migration.
UPDATE campuscore_auth."User"
SET "status" = 'LOCKED',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "status" = 'ACTIVE'
  AND (
        "email" LIKE '%@campuscore.demo'
        OR "email" IN ('student@campuscore.edu', 'lecturer@campuscore.edu', 'admin@campuscore.edu')
      );
