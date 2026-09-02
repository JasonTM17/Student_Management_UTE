ALTER TABLE assistant.knowledge_document ADD COLUMN IF NOT EXISTS domain VARCHAR(48) DEFAULT 'THESIS';
ALTER TABLE assistant.knowledge_document_revision ADD COLUMN IF NOT EXISTS domain VARCHAR(48) DEFAULT 'THESIS';
UPDATE assistant.knowledge_document_revision r
SET domain = COALESCE((SELECT d.domain FROM assistant.knowledge_document d WHERE d.id = r.document_id), 'THESIS')
WHERE domain IS NULL;

ALTER TABLE assistant.chat_citation ADD COLUMN IF NOT EXISTS corpus_version VARCHAR(120);
ALTER TABLE assistant.chat_citation ADD COLUMN IF NOT EXISTS corpus_hash VARCHAR(64);
ALTER TABLE assistant.chat_citation ADD COLUMN IF NOT EXISTS release_id UUID;
