-- V92: the DISABLE purpose for two-factor challenges (round-4 verified fix).
--
-- Turning 2FA off used to require only the password — the second factor was
-- never asked when it was being removed, so a stolen session plus a phished
-- password could unenroll the victim without touching their inbox. Disable
-- now mirrors ENABLE: password starts a challenge, the emailed code confirms
-- it. The purpose CHECK from V85 gains the DISABLE value; V85 used an inline
-- (auto-named) CHECK, which PostgreSQL names <table>_<column>_check.

ALTER TABLE campuscore_auth."TwoFactorChallenge"
    DROP CONSTRAINT IF EXISTS two_factor_challenge_purpose_check;

ALTER TABLE campuscore_auth."TwoFactorChallenge"
    ADD CONSTRAINT two_factor_challenge_purpose_v91
    CHECK ("purpose" IN ('LOGIN', 'ENABLE', 'DISABLE'));
