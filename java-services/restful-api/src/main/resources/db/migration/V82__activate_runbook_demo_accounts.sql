-- V82: Activate the runbook-documented secondary demo accounts so a fresh
-- clone can actually follow docs/DEMO_RUNBOOK.md. V48 locked the whole
-- %campuscore.demo population (correctly — their credentials are published in
-- git history), but only the three .edu showcase accounts were ever
-- re-activated (V50) or re-passworded with a status fix (V77 touched passwords
-- only). Net effect: admin002@campuscore.demo — the mandatory Four-Eyes second
-- approver — and lecturer002..012 (the council examiners) could never log in
-- on a fresh clone, so the documented governance and council walkthroughs were
-- broken out of the box.
--
-- Scope is deliberately the exact runbook set (same discipline as V48/V50/V77):
-- the remaining seeded .demo personas (students, truongkhoa, ...) stay LOCKED.
-- On any host where demo logins must not work, DemoAccountGate re-applies
-- LOCKED to exactly this list on every boot (DEMO_ACCOUNTS_ENABLED=false, the
-- render.yaml/prod default) — the migration and the gate's email list are
-- kept in sync by DemoAccountGateRunbookParityTest.
UPDATE campuscore_auth."User"
SET "status" = 'ACTIVE',
    "failedLoginAttempts" = 0,
    "lockedUntil" = NULL,
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "email" IN (
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
