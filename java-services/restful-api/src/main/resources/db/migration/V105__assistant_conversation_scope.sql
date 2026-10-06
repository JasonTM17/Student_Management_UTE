-- V105: persist the request scope on assistant conversations.
--
-- ChatRequest.scope chose between the academic and specialized corpora but was
-- never stored: resuming a specialized conversation through the academic
-- panel re-answered it against the academic corpus (and listed it in academic
-- history). The column records the corpus a conversation belongs to so a
-- resume always retrieves from the same corpus that created it.

ALTER TABLE assistant.chat_conversation
    ADD COLUMN IF NOT EXISTS scope VARCHAR(16) NOT NULL DEFAULT 'academic';

ALTER TABLE assistant.chat_conversation
    ADD CONSTRAINT assistant_chat_scope_valid CHECK (scope IN ('academic', 'specialized'));

CREATE INDEX IF NOT EXISTS assistant_chat_scope_idx
    ON assistant.chat_conversation (owner_id, scope, updated_at DESC);
