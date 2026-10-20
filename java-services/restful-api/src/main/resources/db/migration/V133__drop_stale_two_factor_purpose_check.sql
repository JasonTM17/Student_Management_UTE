-- V133: drop the stale pre-V92 purpose CHECK on TwoFactorChallenge.
--
-- V92 intended to widen the purpose CHECK with 'DISABLE', but it dropped
-- "two_factor_challenge_purpose_check" — a name that never existed. V85's
-- inline CHECK was auto-named "TwoFactorChallenge_purpose_check" (the quoted
-- table name keeps its case in the generated name), and IF EXISTS made the
-- wrong-name drop silent, so the old constraint stayed behind and keeps
-- rejecting 'DISABLE' inserts on real PostgreSQL. The intended replacement
-- "two_factor_challenge_purpose_v91" already exists and is kept; this
-- migration only removes the stale restrictive constraint.
ALTER TABLE campuscore_auth."TwoFactorChallenge"
    DROP CONSTRAINT IF EXISTS "TwoFactorChallenge_purpose_check";
