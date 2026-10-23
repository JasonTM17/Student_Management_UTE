-- H2 twin of production V140__thesis_topic_review_outcome_columns.sql.
-- Nullable-only additions, no data rewrite.

ALTER TABLE thesis.thesis_topic
    ADD COLUMN IF NOT EXISTS gvpb_waived_reason VARCHAR(500);
ALTER TABLE thesis.thesis_topic
    ADD COLUMN IF NOT EXISTS gvpb_waived_by VARCHAR(120);
ALTER TABLE thesis.thesis_topic
    ADD COLUMN IF NOT EXISTS gvpb_waived_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE thesis.thesis_topic
    ADD COLUMN IF NOT EXISTS result_reason VARCHAR(500);
ALTER TABLE thesis.thesis_topic
    ADD COLUMN IF NOT EXISTS result_by VARCHAR(120);
ALTER TABLE thesis.thesis_topic
    ADD COLUMN IF NOT EXISTS result_at TIMESTAMP WITH TIME ZONE;
