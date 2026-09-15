-- Feedback item 7: a thesis report may be submitted as an attached Word/PDF
-- file next to the legacy external link. Existing link rows stay valid; at
-- least one artifact (url or file) must be present.
ALTER TABLE thesis.thesis_group_report ALTER COLUMN url DROP NOT NULL;

ALTER TABLE thesis.thesis_group_report
    ADD COLUMN IF NOT EXISTS file_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS file_type VARCHAR(120),
    ADD COLUMN IF NOT EXISTS file_size BIGINT,
    ADD COLUMN IF NOT EXISTS file_data BYTEA;

ALTER TABLE thesis.thesis_group_report DROP CONSTRAINT IF EXISTS ck_report_artifact;
ALTER TABLE thesis.thesis_group_report
    ADD CONSTRAINT ck_report_artifact CHECK (url IS NOT NULL OR file_data IS NOT NULL);
