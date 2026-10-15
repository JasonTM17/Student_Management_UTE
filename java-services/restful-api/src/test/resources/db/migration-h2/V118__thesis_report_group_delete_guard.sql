-- H2 twin of production V117__thesis_report_group_delete_guard.sql.
--
-- Production resolves and drops the live FK name, then re-adds it as
-- ON DELETE RESTRICT. H2 names an inline REFERENCES constraint with an
-- unpredictable generated identifier, so the deterministic equivalent is a
-- table rebuild: copy every row into a restricted-shape table, drop the old
-- one, rename, and re-create the constraint/index that lived outside the
-- table definition (ck_report_artifact is inline here; the storage-key unique
-- index is non-partial because H2 has no filtered indexes, same as V13).
--
-- Like production, a group carrying a report can no longer be deleted — the
-- DELETE must remove the report row explicitly first.

-- H2 constraint names live in schema scope, so the artifact check must leave
-- the old table before the replacement table may reuse the name. The stored
-- rows already satisfy the check.
ALTER TABLE thesis.thesis_group_report DROP CONSTRAINT IF EXISTS ck_report_artifact;

CREATE TABLE thesis.thesis_group_report_restricted (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL UNIQUE,
    round_id UUID NOT NULL REFERENCES thesis.thesis_registration_round (id),
    submitted_by VARCHAR(120) NOT NULL,
    title VARCHAR(240),
    url VARCHAR(500),
    note VARCHAR(500),
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    file_name VARCHAR(255),
    file_type VARCHAR(120),
    file_size BIGINT,
    file_data BYTEA,
    storage_provider VARCHAR(32),
    storage_bucket VARCHAR(80),
    storage_key VARCHAR(512),
    file_sha256 CHAR(64),
    CONSTRAINT ck_report_artifact CHECK (
        url IS NOT NULL OR file_data IS NOT NULL OR storage_key IS NOT NULL
    ),
    CONSTRAINT thesis_group_report_group_id_fkey
        FOREIGN KEY (group_id) REFERENCES thesis.thesis_group (id) ON DELETE RESTRICT
);

INSERT INTO thesis.thesis_group_report_restricted (
    id, group_id, round_id, submitted_by, title, url, note,
    submitted_at, updated_at,
    file_name, file_type, file_size, file_data,
    storage_provider, storage_bucket, storage_key, file_sha256
)
SELECT id, group_id, round_id, submitted_by, title, url, note,
       submitted_at, updated_at,
       file_name, file_type, file_size, file_data,
       storage_provider, storage_bucket, storage_key, file_sha256
FROM thesis.thesis_group_report;

DROP TABLE thesis.thesis_group_report;

ALTER TABLE thesis.thesis_group_report_restricted RENAME TO thesis_group_report;

CREATE UNIQUE INDEX IF NOT EXISTS thesis_group_report_storage_key_unique
    ON thesis.thesis_group_report (storage_provider, storage_bucket, storage_key);
