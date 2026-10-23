-- H2 twin of PostgreSQL V139: records the pre-cancel status so a cancelled
-- round can be restored. NULL on rows cancelled earlier restores to DRAFT.
ALTER TABLE thesis.thesis_registration_round
    ADD COLUMN IF NOT EXISTS cancelled_from_status VARCHAR(32);

ALTER TABLE thesis.thesis_registration_round
    DROP CONSTRAINT IF EXISTS thesis_round_cancelled_from_status_valid;

ALTER TABLE thesis.thesis_registration_round
    ADD CONSTRAINT thesis_round_cancelled_from_status_valid
    CHECK (cancelled_from_status IS NULL OR cancelled_from_status IN
        ('DRAFT', 'PROPOSAL_OPEN', 'PROPOSALS_PUBLISHED',
         'REGISTRATION_OPEN', 'REGISTRATION_CLOSED'));
