-- H2 twin of V46__thesis_report_object_storage.sql.
ALTER TABLE thesis.thesis_group_report ADD COLUMN IF NOT EXISTS storage_provider VARCHAR(32);
ALTER TABLE thesis.thesis_group_report ADD COLUMN IF NOT EXISTS storage_bucket VARCHAR(80);
ALTER TABLE thesis.thesis_group_report ADD COLUMN IF NOT EXISTS storage_key VARCHAR(512);
ALTER TABLE thesis.thesis_group_report ADD COLUMN IF NOT EXISTS file_sha256 CHAR(64);

ALTER TABLE thesis.thesis_group_report DROP CONSTRAINT IF EXISTS ck_report_artifact;
ALTER TABLE thesis.thesis_group_report
    ADD CONSTRAINT ck_report_artifact CHECK (
        url IS NOT NULL OR file_data IS NOT NULL OR storage_key IS NOT NULL
    );

CREATE UNIQUE INDEX IF NOT EXISTS thesis_group_report_storage_key_unique
    ON thesis.thesis_group_report (storage_provider, storage_bucket, storage_key);
