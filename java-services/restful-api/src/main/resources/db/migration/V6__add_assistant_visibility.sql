ALTER TABLE assistant.knowledge_document
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE assistant.knowledge_document
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC';

CREATE INDEX IF NOT EXISTS assistant_knowledge_active_locale_idx
    ON assistant.knowledge_document (active, visibility, locale, priority);
