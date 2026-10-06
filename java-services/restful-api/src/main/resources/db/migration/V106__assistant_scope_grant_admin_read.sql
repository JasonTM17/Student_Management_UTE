-- Assistant follow-on RLS grants for V84-provisioned boundaries.
--
-- 1) V105 introduced assistant.chat_conversation.scope. ThesisAssistantService
--    stamps it on the first turn of every conversation, but V84's column-level
--    UPDATE grant covered only (title, state, updated_at, expires_at), so the
--    provisioned runtime role rejected the write with
--    "permission denied for column scope". Column-level grants are additive, so
--    grant the missing column instead of re-granting the full list.
GRANT UPDATE (scope) ON assistant.chat_conversation TO campuscore_assistant_runtime;

-- 2) Governed cross-owner reads for the admin feedback console.
--    AssistantFeedbackAdminService enters an ADMIN_GOVERNANCE boundary (the same
--    trusted scope the knowledge admin CRUD uses, additionally requiring
--    app.assistant.admin='true'), so these SELECT-only policies deliberately
--    carry no owner predicate: aggregation must span owners. Writes stay
--    owner-scoped — no admin write policy is granted.
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
