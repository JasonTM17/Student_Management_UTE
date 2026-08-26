-- H2 parity migration. Keep V5 immutable and avoid PostgreSQL-only syntax.
CREATE TABLE IF NOT EXISTS assistant.chat_conversation (
    id UUID PRIMARY KEY, owner_id VARCHAR(120) NOT NULL, title VARCHAR(160),
    locale VARCHAR(2) NOT NULL DEFAULT 'vi',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '90' DAY),
    state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', archived_at TIMESTAMP WITH TIME ZONE, archived_by VARCHAR(120)
);
ALTER TABLE assistant.chat_conversation ADD COLUMN IF NOT EXISTS state VARCHAR(16) DEFAULT 'ACTIVE';
ALTER TABLE assistant.chat_conversation ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE assistant.chat_conversation ADD COLUMN IF NOT EXISTS archived_by VARCHAR(120);
ALTER TABLE assistant.chat_conversation ADD CONSTRAINT IF NOT EXISTS assistant_chat_conversation_state_valid CHECK (state IN ('PENDING', 'ACTIVE', 'PURGED'));
CREATE INDEX IF NOT EXISTS assistant_chat_owner_state_updated_idx ON assistant.chat_conversation (owner_id, state, updated_at DESC);

ALTER TABLE assistant.chat_message ADD COLUMN IF NOT EXISTS turn_id UUID;
ALTER TABLE assistant.chat_message ADD COLUMN IF NOT EXISTS ordinal INTEGER;
UPDATE assistant.chat_message SET turn_id = RANDOM_UUID(), ordinal = COALESCE(ordinal, 0) WHERE turn_id IS NULL;
CREATE INDEX IF NOT EXISTS assistant_chat_message_turn_idx ON assistant.chat_message (turn_id, role);
CREATE UNIQUE INDEX IF NOT EXISTS assistant_chat_message_one_role_per_turn_idx ON assistant.chat_message (turn_id, role);

ALTER TABLE assistant.chat_citation ADD COLUMN IF NOT EXISTS domain VARCHAR(48) DEFAULT 'THESIS';
ALTER TABLE assistant.chat_citation ALTER COLUMN document_id SET NULL;
ALTER TABLE assistant.chat_citation ADD COLUMN IF NOT EXISTS source_kind VARCHAR(24) DEFAULT 'CURATED';
ALTER TABLE assistant.chat_citation ADD COLUMN IF NOT EXISTS source_id VARCHAR(180);
ALTER TABLE assistant.chat_citation ADD COLUMN IF NOT EXISTS revision_id UUID;
ALTER TABLE assistant.chat_citation ADD COLUMN IF NOT EXISTS revision_version INTEGER;
ALTER TABLE assistant.chat_citation ADD COLUMN IF NOT EXISTS catalog_entity_type VARCHAR(48);
ALTER TABLE assistant.chat_citation ADD COLUMN IF NOT EXISTS catalog_entity_id VARCHAR(180);
ALTER TABLE assistant.chat_citation ADD COLUMN IF NOT EXISTS catalog_updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE assistant.chat_citation ADD COLUMN IF NOT EXISTS snapshot_hash VARCHAR(64);
ALTER TABLE assistant.chat_citation ADD COLUMN IF NOT EXISTS ordinal INTEGER DEFAULT 0;
UPDATE assistant.chat_citation SET source_id = COALESCE(source_id, CAST(document_id AS VARCHAR)), snapshot_hash = COALESCE(snapshot_hash, HASH('SHA256', STRINGTOUTF8(CONCAT(CAST(document_id AS VARCHAR), '|', slug, '|', title, '|', source, '|', locale, '|', excerpt)))) WHERE source_id IS NULL OR snapshot_hash IS NULL;
ALTER TABLE assistant.chat_citation ADD CONSTRAINT IF NOT EXISTS assistant_chat_citation_source_kind_valid CHECK (source_kind IN ('CURATED', 'CATALOG', 'LEGACY_SNAPSHOT'));
CREATE INDEX IF NOT EXISTS assistant_chat_citation_provenance_idx ON assistant.chat_citation (domain, source_kind, source_id, revision_id, catalog_entity_id);

CREATE TABLE IF NOT EXISTS assistant.chat_turn_ledger (
    turn_id UUID PRIMARY KEY, owner_id VARCHAR(120) NOT NULL, client_request_id UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL, conversation_id UUID,
    state VARCHAR(24) NOT NULL, lease_owner VARCHAR(160), lease_generation BIGINT NOT NULL DEFAULT 0,
    lease_expires_at TIMESTAMP WITH TIME ZONE, dispatched_at TIMESTAMP WITH TIME ZONE,
    quota_reserved BOOLEAN NOT NULL DEFAULT FALSE, result_message_id UUID, terminal_reason VARCHAR(48),
    source_snapshot_hash VARCHAR(64), created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    purged_at TIMESTAMP WITH TIME ZONE, tombstone_until TIMESTAMP WITH TIME ZONE,
    UNIQUE (owner_id, client_request_id),
    CONSTRAINT assistant_turn_state_valid CHECK (state IN ('RESERVED', 'SNAPSHOT_READY', 'DISPATCHED', 'COMPLETED', 'CANCELLED', 'FAILED_PRE_DISPATCH', 'FAILED_AMBIGUOUS', 'PURGED')),
    CONSTRAINT assistant_turn_lease_generation_valid CHECK (lease_generation >= 0),
    CONSTRAINT assistant_turn_hash_valid CHECK (LENGTH(request_hash) = 64)
);
ALTER TABLE assistant.chat_turn_ledger ADD COLUMN IF NOT EXISTS created_conversation BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE assistant.chat_turn_ledger ADD CONSTRAINT IF NOT EXISTS assistant_turn_conversation_fk
    FOREIGN KEY (conversation_id) REFERENCES assistant.chat_conversation(id) ON DELETE SET NULL;
ALTER TABLE assistant.chat_turn_ledger ADD CONSTRAINT IF NOT EXISTS assistant_turn_result_message_fk
    FOREIGN KEY (result_message_id) REFERENCES assistant.chat_message(id) ON DELETE SET NULL;
-- H2 has no PostgreSQL-style filtered indexes. Nullable generated columns keep
-- the same invariant: only an in-flight state contributes to the unique key,
-- while terminal/purged rows remain replayable for the same conversation.
ALTER TABLE assistant.chat_turn_ledger ADD COLUMN IF NOT EXISTS active_owner_id VARCHAR(120)
    AS (CASE WHEN conversation_id IS NOT NULL AND state IN ('RESERVED', 'SNAPSHOT_READY', 'DISPATCHED') THEN owner_id ELSE NULL END);
ALTER TABLE assistant.chat_turn_ledger ADD COLUMN IF NOT EXISTS active_conversation_id UUID
    AS (CASE WHEN conversation_id IS NOT NULL AND state IN ('RESERVED', 'SNAPSHOT_READY', 'DISPATCHED') THEN conversation_id ELSE NULL END);
CREATE UNIQUE INDEX IF NOT EXISTS assistant_turn_active_conversation_idx
    ON assistant.chat_turn_ledger (active_owner_id, active_conversation_id);
CREATE INDEX IF NOT EXISTS assistant_turn_owner_updated_idx ON assistant.chat_turn_ledger (owner_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS assistant_turn_expiry_idx ON assistant.chat_turn_ledger (state, tombstone_until, updated_at);
CREATE INDEX IF NOT EXISTS assistant_usage_bucket_retention_idx ON assistant.usage_bucket (bucket_date, scope);

CREATE TABLE IF NOT EXISTS assistant.provider_dispatch_registry (
    owner_id VARCHAR(120) NOT NULL, client_request_id UUID NOT NULL, lease_generation BIGINT NOT NULL,
    provider_handle VARCHAR(180), state VARCHAR(24) NOT NULL DEFAULT 'DISPATCHED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (owner_id, client_request_id, lease_generation)
);

CREATE TABLE IF NOT EXISTS assistant.chat_message_feedback (
    message_id UUID NOT NULL, owner_id VARCHAR(120) NOT NULL, rating VARCHAR(4) NOT NULL,
    reason VARCHAR(16), created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id, owner_id),
    CONSTRAINT assistant_feedback_rating_valid CHECK (rating IN ('UP', 'DOWN')),
    CONSTRAINT assistant_feedback_reason_valid CHECK (reason IS NULL OR reason IN ('HELPFUL', 'CLEAR', 'INCORRECT', 'OUTDATED', 'NOT_RELEVANT', 'UNSAFE'))
);
ALTER TABLE assistant.chat_message_feedback ADD CONSTRAINT IF NOT EXISTS assistant_feedback_message_fk FOREIGN KEY (message_id) REFERENCES assistant.chat_message(id) ON DELETE CASCADE;
ALTER TABLE assistant.knowledge_document ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE assistant.knowledge_document ADD COLUMN IF NOT EXISTS archived_by VARCHAR(120);
CREATE INDEX IF NOT EXISTS assistant_knowledge_archive_idx ON assistant.knowledge_document (active, archived_at, updated_at DESC);

UPDATE assistant.chat_citation SET source_kind='LEGACY_SNAPSHOT'
WHERE source_kind='CURATED' AND revision_version=0;
UPDATE assistant.chat_message SET reason_code='LEGACY_COMPLETED'
WHERE role='ASSISTANT' AND reason_code <> 'LEGACY_COMPLETED';
