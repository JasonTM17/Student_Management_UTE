-- Restore the dedicated KLTN defense date removed by the identity migration.
-- Presence/conditional business rules remain in ThesisMutationService so
-- existing historical rows can be upgraded without rewriting academic data.
-- Fence round writes while the historical shape is censused and constrained;
-- after V66, rollback is forward-fix only (the old writer does not know the
-- schedule oracle).
LOCK TABLE thesis.thesis_registration_round IN SHARE ROW EXCLUSIVE MODE;

ALTER TABLE thesis.thesis_registration_round
    ADD COLUMN IF NOT EXISTS defense_date TIMESTAMPTZ;

-- V30 is the only migration that copied the registration window into both
-- lecturer-submit columns. Repair exactly that known mechanical shape before
-- installing the database oracle. Do not rewrite independently authored dates.
UPDATE thesis.thesis_registration_round
SET lecturer_submit_end = registration_start,
    lecturer_submit_start = registration_start - INTERVAL '1 second'
WHERE lecturer_submit_start = registration_start
  AND lecturer_submit_end = registration_end
  AND registration_end > registration_start;

DO $$
DECLARE
    invalid_round_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_round_count
    FROM thesis.thesis_registration_round
    WHERE lecturer_submit_end <= lecturer_submit_start
       OR registration_end <= registration_start
       OR registration_start < lecturer_submit_end
       OR (proposal_publish_at IS NOT NULL AND proposal_publish_at < lecturer_submit_end)
       OR (gvpb_deadline IS NOT NULL AND gvpb_deadline < registration_end)
       OR (report_date IS NOT NULL AND gvpb_deadline IS NOT NULL AND report_date < gvpb_deadline)
       OR (defense_date IS NOT NULL AND report_date IS NOT NULL AND defense_date < report_date);
    IF invalid_round_count > 0 THEN
        RAISE EXCEPTION
            'V66 stopped: % thesis rounds have authored schedule dates that require governance review',
            invalid_round_count;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'thesis_round_schedule_order_valid'
    ) THEN
        ALTER TABLE thesis.thesis_registration_round
            ADD CONSTRAINT thesis_round_schedule_order_valid
            CHECK (
                lecturer_submit_end > lecturer_submit_start
                AND registration_end > registration_start
                AND registration_start >= lecturer_submit_end
                AND (proposal_publish_at IS NULL OR proposal_publish_at >= lecturer_submit_end)
                AND (gvpb_deadline IS NULL OR gvpb_deadline >= registration_end)
                AND (report_date IS NULL OR gvpb_deadline IS NULL OR report_date >= gvpb_deadline)
                AND (defense_date IS NULL OR report_date IS NULL OR defense_date >= report_date)
            );
    END IF;
END $$;
