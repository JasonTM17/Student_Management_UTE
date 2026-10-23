-- H2 twin of production V141__thesis_outcome_visibility_and_publication_guard.sql.
-- Only the thesis-schema column additions are mirrored: the H2 tree carries no
-- notifications.notification table (engagement tests create it in-test) and no
-- plpgsql membership validators, so the notification index/dedupe and the
-- function replaces exist in the production migration only.

ALTER TABLE thesis.thesis_registration_round
    ADD COLUMN IF NOT EXISTS results_published_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE thesis.thesis_group
    ADD COLUMN IF NOT EXISTS size_exception_reason VARCHAR(500);
ALTER TABLE thesis.thesis_group
    ADD COLUMN IF NOT EXISTS size_exception_by VARCHAR(120);
ALTER TABLE thesis.thesis_group
    ADD COLUMN IF NOT EXISTS size_exception_at TIMESTAMP WITH TIME ZONE;
