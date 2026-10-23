-- A cancelled round is no longer a dead end: the column remembers which
-- status the round held at the moment of cancellation so an administrator can
-- undo an accidental cancel. The value is written by cancelRound and cleared
-- by the restore transition, so a non-NULL value means "this row is currently
-- CANCELLED and knows where to return".
--
-- Rows cancelled before this column existed keep NULL. Their restore falls
-- back to DRAFT — the only status that fabricates no progress — letting the
-- administrator re-advance the round through the normal transition chain.
ALTER TABLE thesis.thesis_registration_round
    ADD COLUMN IF NOT EXISTS cancelled_from_status VARCHAR(32);

ALTER TABLE thesis.thesis_registration_round
    DROP CONSTRAINT IF EXISTS thesis_round_cancelled_from_status_valid;

ALTER TABLE thesis.thesis_registration_round
    ADD CONSTRAINT thesis_round_cancelled_from_status_valid
    CHECK (cancelled_from_status IS NULL OR cancelled_from_status IN
        ('DRAFT', 'PROPOSAL_OPEN', 'PROPOSALS_PUBLISHED',
         'REGISTRATION_OPEN', 'REGISTRATION_CLOSED'));
