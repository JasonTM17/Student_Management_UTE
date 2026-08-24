CREATE TABLE IF NOT EXISTS assistant.knowledge_document_revision (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES assistant.knowledge_document(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    state VARCHAR(24) NOT NULL,
    locale VARCHAR(8) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    source TEXT NOT NULL,
    priority SMALLINT NOT NULL DEFAULT 100,
    created_by VARCHAR(120) NOT NULL,
    reviewed_by VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    -- H2 has no filtered indexes; this generated nullable key mirrors
    -- PostgreSQL's one-published-revision partial unique index.
    published_document_id UUID AS (CASE WHEN state = 'PUBLISHED' THEN document_id ELSE NULL END),
    UNIQUE (document_id, version),
    CONSTRAINT assistant_revision_state_valid CHECK (state IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'ARCHIVED'))
);
CREATE INDEX IF NOT EXISTS assistant_revision_published_idx ON assistant.knowledge_document_revision (state, locale, priority);
CREATE UNIQUE INDEX IF NOT EXISTS assistant_revision_one_published_idx
    ON assistant.knowledge_document_revision (published_document_id);
INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority, created_by, reviewed_by, published_at)
SELECT RANDOM_UUID(), id, 1, 'PUBLISHED', locale, slug, title, content, source, priority,
       'system-seed', 'system-seed', CURRENT_TIMESTAMP
FROM assistant.knowledge_document d
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document_revision r WHERE r.document_id = d.id AND r.version = 1);

CREATE TABLE IF NOT EXISTS assistant.chat_conversation (
    id UUID PRIMARY KEY, owner_id VARCHAR(120) NOT NULL, title VARCHAR(160), locale VARCHAR(2) NOT NULL DEFAULT 'vi',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '90' DAY),
    CONSTRAINT assistant_chat_locale_valid CHECK (locale IN ('vi', 'en'))
);
CREATE INDEX IF NOT EXISTS assistant_chat_owner_updated_idx ON assistant.chat_conversation (owner_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS assistant_chat_expiry_idx ON assistant.chat_conversation (expires_at);
CREATE TABLE IF NOT EXISTS assistant.chat_message (
    id UUID PRIMARY KEY, conversation_id UUID NOT NULL REFERENCES assistant.chat_conversation(id) ON DELETE CASCADE, role VARCHAR(16) NOT NULL, content TEXT NOT NULL,
    model VARCHAR(80) NOT NULL, degraded BOOLEAN NOT NULL DEFAULT FALSE, reason_code VARCHAR(48) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT assistant_chat_role_valid CHECK (role IN ('USER', 'ASSISTANT'))
);
CREATE INDEX IF NOT EXISTS assistant_chat_message_order_idx ON assistant.chat_message (conversation_id, created_at, id);
CREATE TABLE IF NOT EXISTS assistant.chat_citation (
    id UUID PRIMARY KEY, message_id UUID NOT NULL REFERENCES assistant.chat_message(id) ON DELETE CASCADE, document_id UUID NOT NULL, slug VARCHAR(180) NOT NULL,
    title TEXT NOT NULL, source TEXT NOT NULL, locale VARCHAR(8) NOT NULL, excerpt TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS assistant_chat_citation_message_idx ON assistant.chat_citation (message_id);
CREATE TABLE IF NOT EXISTS assistant.usage_bucket (
    bucket_date DATE NOT NULL, owner_id VARCHAR(120) NOT NULL, scope VARCHAR(16) NOT NULL DEFAULT 'USER',
    request_count INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (bucket_date, owner_id, scope),
    CONSTRAINT assistant_usage_scope_valid CHECK (scope IN ('USER', 'GLOBAL')),
    CONSTRAINT assistant_usage_nonnegative CHECK (request_count >= 0)
);
CREATE TABLE IF NOT EXISTS assistant.knowledge_document_audit (
    id UUID PRIMARY KEY, revision_id UUID NOT NULL REFERENCES assistant.knowledge_document_revision(id) ON DELETE CASCADE, action VARCHAR(32) NOT NULL,
    actor_id VARCHAR(120) NOT NULL, note TEXT, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
