-- H2 mirror of V105__assistant_conversation_scope.sql (Postgres sequence):
-- persists the request scope so a resumed conversation retrieves from the
-- same corpus that created it.
ALTER TABLE assistant.chat_conversation
    ADD COLUMN IF NOT EXISTS scope VARCHAR(16) NOT NULL DEFAULT 'academic';
ALTER TABLE assistant.chat_conversation
    ADD CONSTRAINT assistant_chat_scope_valid CHECK (scope IN ('academic', 'specialized'));
CREATE INDEX IF NOT EXISTS assistant_chat_scope_idx
    ON assistant.chat_conversation (owner_id, scope, updated_at DESC);
