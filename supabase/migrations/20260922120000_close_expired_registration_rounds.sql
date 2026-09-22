-- Supabase twin of V76__close_expired_registration_rounds.sql.
-- Close registration rounds whose admission window has already ended so the
-- status column agrees with the window and clients stop picking an expired
-- OPEN round.
UPDATE academic."RegistrationRound"
SET "status" = 'CLOSED',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "status" = 'OPEN'
  AND "windowEnd" < CURRENT_TIMESTAMP;
