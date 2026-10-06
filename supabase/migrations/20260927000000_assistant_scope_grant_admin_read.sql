-- Assistant follow-on RLS grants (mirrors Flyway V106 for provisioned databases).
--
-- 1) chat_conversation.scope was added after the original runtime grant; the
--    first-turn scope stamp needs a column-level UPDATE grant on the new column.
GRANT UPDATE (scope) ON assistant.chat_conversation TO campuscore_assistant_runtime;

-- 2) Governed cross-owner SELECT for the admin feedback console. The service
--    enters an ADMIN_GOVERNANCE boundary, so these policies carry no owner
--    predicate by design. Read-only: no admin write policy is granted.
DROP POLICY IF EXISTS campuscore_assistant_feedback_admin_read ON assistant.chat_message_feedback;
CREATE POLICY campuscore_assistant_feedback_admin_read ON assistant.chat_message_feedback
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'ADMIN_GOVERNANCE'
        AND current_setting('app.assistant.admin', true) = 'true'
    );

DROP POLICY IF EXISTS campuscore_assistant_message_admin_read ON assistant.chat_message;
CREATE POLICY campuscore_assistant_message_admin_read ON assistant.chat_message
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'ADMIN_GOVERNANCE'
        AND current_setting('app.assistant.admin', true) = 'true'
    );
