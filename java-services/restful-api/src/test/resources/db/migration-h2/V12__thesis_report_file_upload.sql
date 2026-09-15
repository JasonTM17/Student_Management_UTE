-- H2 twin of V44__thesis_report_file_upload.sql (see the Postgres chain for
-- commentary). H2 needs one ADD COLUMN per statement, and in PostgreSQL mode
-- the document bytes use the BYTEA alias; the artifact check uses the
-- portable boolean form because num_nonnulls is not available on H2.
ALTER TABLE thesis.thesis_group_report ALTER COLUMN url SET NULL;

ALTER TABLE thesis.thesis_group_report ADD COLUMN IF NOT EXISTS file_name VARCHAR(255);
ALTER TABLE thesis.thesis_group_report ADD COLUMN IF NOT EXISTS file_type VARCHAR(120);
ALTER TABLE thesis.thesis_group_report ADD COLUMN IF NOT EXISTS file_size BIGINT;
ALTER TABLE thesis.thesis_group_report ADD COLUMN IF NOT EXISTS file_data BYTEA;

ALTER TABLE thesis.thesis_group_report DROP CONSTRAINT IF EXISTS ck_report_artifact;
ALTER TABLE thesis.thesis_group_report
    ADD CONSTRAINT ck_report_artifact CHECK (url IS NOT NULL OR file_data IS NOT NULL);
