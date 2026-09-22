-- V76: Close registration rounds whose admission window has already ended.
-- The demo seed left rows with status='OPEN' even though windowEnd passed
-- (round-registration-demo ended 2026-08-30). Clients pick the first OPEN
-- round and then fail eligibility with WINDOW_CLOSED, which renders the
-- honest but wrong "no registration round is open" state during the current
-- live window. Status must agree with the window: a round whose window has
-- passed is CLOSED no matter what the seed wrote.
UPDATE academic."RegistrationRound"
SET "status" = 'CLOSED',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "status" = 'OPEN'
  AND "windowEnd" < CURRENT_TIMESTAMP;
