-- Campus assistant domains and an immutable, atomically activated runtime
-- projection. Supabase is the production authoring source; PostgreSQL serves
-- only the validated release selected by the singleton pointer below.
CREATE SCHEMA IF NOT EXISTS assistant;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE assistant.chat_citation
    ADD COLUMN IF NOT EXISTS corpus_version VARCHAR(120),
    ADD COLUMN IF NOT EXISTS corpus_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS release_id UUID;
CREATE INDEX IF NOT EXISTS assistant_chat_citation_release_idx
    ON assistant.chat_citation (release_id, corpus_hash);

ALTER TABLE assistant.knowledge_document
    ADD COLUMN IF NOT EXISTS domain VARCHAR(48) NOT NULL DEFAULT 'THESIS';
ALTER TABLE assistant.knowledge_document_revision
    ADD COLUMN IF NOT EXISTS domain VARCHAR(48) NOT NULL DEFAULT 'THESIS';

UPDATE assistant.knowledge_document_revision r
SET domain = COALESCE(NULLIF(d.domain, ''), 'THESIS')
FROM assistant.knowledge_document d
WHERE d.id = r.document_id;

ALTER TABLE assistant.knowledge_document
    DROP CONSTRAINT IF EXISTS assistant_knowledge_domain_valid;
ALTER TABLE assistant.knowledge_document
    ADD CONSTRAINT assistant_knowledge_domain_valid
        CHECK (domain IN ('THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ'));
ALTER TABLE assistant.knowledge_document_revision
    DROP CONSTRAINT IF EXISTS assistant_revision_domain_valid;
ALTER TABLE assistant.knowledge_document_revision
    ADD CONSTRAINT assistant_revision_domain_valid
        CHECK (domain IN ('THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ'));
CREATE INDEX IF NOT EXISTS assistant_knowledge_domain_idx
    ON assistant.knowledge_document (domain, active, visibility, locale, priority);
CREATE INDEX IF NOT EXISTS assistant_revision_domain_idx
    ON assistant.knowledge_document_revision (domain, state, locale, priority);

CREATE TABLE IF NOT EXISTS assistant.knowledge_release (
    id UUID PRIMARY KEY,
    corpus_version VARCHAR(120) NOT NULL,
    corpus_hash CHAR(64) NOT NULL,
    row_count INTEGER NOT NULL CHECK (row_count >= 0),
    source VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    manifest JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMPTZ,
    previous_release_id UUID REFERENCES assistant.knowledge_release(id),
    CONSTRAINT assistant_release_source_valid CHECK (source IN ('SUPABASE', 'LEGACY', 'MANUAL')),
    CONSTRAINT assistant_release_status_valid CHECK (status IN ('STAGED', 'VALIDATED', 'PUBLISHED', 'FAILED', 'ARCHIVED')),
    CONSTRAINT assistant_release_hash_valid CHECK (corpus_hash ~ '^[0-9a-f]{64}$')
);
CREATE UNIQUE INDEX IF NOT EXISTS assistant_release_hash_idx
    ON assistant.knowledge_release (corpus_hash);
CREATE INDEX IF NOT EXISTS assistant_release_status_idx
    ON assistant.knowledge_release (status, created_at DESC);

ALTER TABLE assistant.chat_citation
    ADD CONSTRAINT assistant_chat_citation_release_fk
        FOREIGN KEY (release_id) REFERENCES assistant.knowledge_release(id) ON DELETE RESTRICT;

CREATE TABLE IF NOT EXISTS assistant.knowledge_runtime_document (
    release_id UUID NOT NULL REFERENCES assistant.knowledge_release(id) ON DELETE CASCADE,
    source_id VARCHAR(180) NOT NULL,
    revision_id UUID,
    version INTEGER NOT NULL DEFAULT 0,
    domain VARCHAR(48) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    locale VARCHAR(8) NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    source TEXT NOT NULL,
    priority SMALLINT NOT NULL DEFAULT 100,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (release_id, source_id),
    CONSTRAINT assistant_runtime_domain_valid CHECK (domain IN ('THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ')),
    CONSTRAINT assistant_runtime_locale_valid CHECK (locale IN ('vi', 'en', 'both')),
    CONSTRAINT assistant_runtime_visibility_valid CHECK (visibility = 'PUBLIC')
);
CREATE INDEX IF NOT EXISTS assistant_runtime_search_idx
    ON assistant.knowledge_runtime_document (release_id, active, visibility, locale, domain, priority, published_at DESC);

CREATE TABLE IF NOT EXISTS assistant.knowledge_runtime_state (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    active_release_id UUID REFERENCES assistant.knowledge_release(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Build a deterministic first release from the V15 published corpus. This
-- keeps existing installations available while a Supabase release is staged;
-- later syncs can only replace the pointer after complete validation.
WITH canonical AS (
    SELECT d.id::text AS source_id, r.id AS revision_id, r.version, COALESCE(r.domain, d.domain, 'THESIS') AS domain,
           COALESCE(r.slug, d.slug) AS slug, COALESCE(r.locale, d.locale) AS locale,
           COALESCE(r.title, d.title) AS title, COALESCE(r.content, d.content) AS content,
           COALESCE(r.source, d.source) AS source, COALESCE(r.priority, d.priority) AS priority,
           r.published_at
    FROM assistant.knowledge_document d
    JOIN assistant.knowledge_document_revision r
      ON r.document_id = d.id AND r.state = 'PUBLISHED'
    WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
), summary AS (
    SELECT encode(digest(COALESCE(string_agg(concat_ws('|', source_id, domain, slug, locale, title, content, source, priority::text, version::text), E'\n' ORDER BY source_id), ''), 'sha256'), 'hex') AS corpus_hash,
           COUNT(*)::integer AS row_count,
           COALESCE(jsonb_agg(jsonb_build_object('sourceId', source_id, 'domain', domain, 'slug', slug, 'locale', locale) ORDER BY source_id), '[]'::jsonb) AS documents
    FROM canonical
)
INSERT INTO assistant.knowledge_release (id, corpus_version, corpus_hash, row_count, source, status, manifest, created_by)
SELECT '00000000-0000-0000-0000-000000000016'::uuid, 'legacy-v15', corpus_hash, row_count, 'LEGACY', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'legacy-v15', 'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration'
FROM summary
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000016'::uuid);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000016'::uuid, d.id::text, r.id, r.version,
       COALESCE(r.domain, d.domain, 'THESIS'), COALESCE(r.slug, d.slug), COALESCE(r.locale, d.locale),
       COALESCE(r.title, d.title), COALESCE(r.content, d.content), COALESCE(r.source, d.source),
       COALESCE(r.priority, d.priority), TRUE, 'PUBLIC', COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r
  ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document p
      WHERE p.release_id = '00000000-0000-0000-0000-000000000016'::uuid AND p.source_id = d.id::text
  );

INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000016'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = COALESCE(assistant.knowledge_runtime_state.active_release_id, EXCLUDED.active_release_id),
    updated_at = CURRENT_TIMESTAMP;
