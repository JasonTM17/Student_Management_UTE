-- Versioned CampusCore Assistant RLS boundary. Password provisioning is a separate secret-safe operation.
-- This migration never changes the runtime role's LOGIN state or password.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'campuscore_assistant_runtime') THEN
        CREATE ROLE campuscore_assistant_runtime
            NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT
            NOREPLICATION NOBYPASSRLS CONNECTION LIMIT 8;
    ELSE
        IF EXISTS (
            SELECT 1
              FROM pg_roles
             WHERE rolname = 'campuscore_assistant_runtime'
               AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolinherit OR rolreplication
                    OR rolbypassrls OR rolconnlimit <> 8)
        ) THEN
            RAISE EXCEPTION 'campuscore_assistant_runtime has unsafe role attributes';
        END IF;
        -- Supabase automatically grants every newly created role to `postgres`
        -- (member direction only, so the dashboard postgres user can SET ROLE
        -- into it). That single auto-grant is expected and harmless: it gives
        -- postgres access to this role, never the reverse. Anything else — the
        -- runtime role holding membership in another role, or any member other
        -- than postgres — breaks the isolation boundary and must fail loudly.
        -- Corrective note 2026-09-26: the original both-directions check never
        -- applied successfully on Supabase (auto-re-grant makes it unsatisfiable);
        -- this migration had not been applied in any environment when relaxed.
        IF EXISTS (
            SELECT 1
              FROM pg_auth_members m
              JOIN pg_roles runtime ON runtime.oid = m.roleid
              JOIN pg_roles member ON member.oid = m.member
             WHERE runtime.rolname = 'campuscore_assistant_runtime'
               AND member.rolname <> 'postgres'
        ) THEN
            RAISE EXCEPTION 'campuscore_assistant_runtime must have no members other than the Supabase-managed postgres grant';
        END IF;
        IF EXISTS (
            SELECT 1
              FROM pg_auth_members m
              JOIN pg_roles runtime ON runtime.oid = m.member
             WHERE runtime.rolname = 'campuscore_assistant_runtime'
        ) THEN
            RAISE EXCEPTION 'campuscore_assistant_runtime must not be a member of any other role';
        END IF;
    END IF;
END
$$;

DO $$
DECLARE
    api_role text;
BEGIN
    IF EXISTS (
        SELECT 1
          FROM pg_policies
         WHERE schemaname = 'assistant'
           AND tablename || '.' || policyname NOT IN (
               'chat_conversation.campuscore_assistant_conversation_owner',
               'chat_conversation.campuscore_assistant_conversation_retention_read',
               'chat_conversation.campuscore_assistant_conversation_retention_update',
               'chat_conversation.campuscore_assistant_conversation_retention_delete',
               'chat_message.campuscore_assistant_message_owner_read',
               'chat_message.campuscore_assistant_message_owner_insert',
               'chat_citation.campuscore_assistant_citation_owner_read',
               'chat_citation.campuscore_assistant_citation_owner_insert',
               'chat_message_feedback.campuscore_assistant_feedback_owner',
               'chat_turn_ledger.campuscore_assistant_turn_owner',
               'chat_turn_ledger.campuscore_assistant_turn_retention_read',
               'chat_turn_ledger.campuscore_assistant_turn_retention_update',
               'chat_turn_ledger.campuscore_assistant_turn_retention_delete',
               'provider_dispatch_registry.campuscore_assistant_dispatch_owner',
               'provider_dispatch_registry.campuscore_assistant_dispatch_retention_read',
               'provider_dispatch_registry.campuscore_assistant_dispatch_retention_update',
               'provider_dispatch_registry.campuscore_assistant_dispatch_retention_delete',
               'usage_bucket.campuscore_assistant_usage_read',
               'usage_bucket.campuscore_assistant_usage_insert',
               'usage_bucket.campuscore_assistant_usage_update',
               'usage_bucket.campuscore_assistant_usage_delete_owner',
               'usage_bucket.campuscore_assistant_usage_retention_read',
               'usage_bucket.campuscore_assistant_usage_retention_delete',
               'knowledge_document.campuscore_assistant_knowledge_document_admin',
               'knowledge_document_revision.campuscore_assistant_knowledge_revision_admin',
               'knowledge_document_audit.campuscore_assistant_knowledge_audit_insert',
               'knowledge_release.campuscore_assistant_release_admin_read',
               'knowledge_release.campuscore_assistant_release_admin_insert',
               'knowledge_release.campuscore_assistant_release_admin_update',
               'knowledge_release.campuscore_assistant_release_user_active_read',
               'knowledge_release.campuscore_assistant_release_projection_read',
               'knowledge_release.campuscore_assistant_release_projection_insert',
               'knowledge_release.campuscore_assistant_release_projection_update',
               'knowledge_runtime_document.campuscore_assistant_runtime_document_user_read',
               'knowledge_runtime_document.campuscore_assistant_runtime_document_admin_read',
               'knowledge_runtime_document.campuscore_assistant_runtime_document_admin_insert',
               'knowledge_runtime_document.campuscore_assistant_runtime_document_projection_read',
               'knowledge_runtime_document.campuscore_assistant_runtime_document_projection_insert',
               'knowledge_runtime_state.campuscore_assistant_runtime_state_user_read',
               'knowledge_runtime_state.campuscore_assistant_runtime_state_admin_read',
               'knowledge_runtime_state.campuscore_assistant_runtime_state_admin_insert',
               'knowledge_runtime_state.campuscore_assistant_runtime_state_admin_update',
               'knowledge_runtime_state.campuscore_assistant_runtime_state_projection_read',
               'knowledge_runtime_state.campuscore_assistant_runtime_state_projection_insert',
               'knowledge_runtime_state.campuscore_assistant_runtime_state_projection_update'
           )
    ) THEN
        RAISE EXCEPTION 'Unreviewed Assistant RLS policies exist; refusing to replace the reviewed policy set';
    END IF;

    REVOKE ALL ON SCHEMA assistant FROM PUBLIC;
    REVOKE ALL ON ALL TABLES IN SCHEMA assistant FROM PUBLIC;
    REVOKE ALL ON ALL SEQUENCES IN SCHEMA assistant FROM PUBLIC;
    REVOKE ALL ON ALL FUNCTIONS IN SCHEMA assistant FROM PUBLIC;

    ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA assistant REVOKE ALL ON TABLES FROM PUBLIC;
    ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA assistant REVOKE ALL ON SEQUENCES FROM PUBLIC;
    ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA assistant REVOKE ALL ON FUNCTIONS FROM PUBLIC;

    FOREACH api_role IN ARRAY ARRAY['anon', 'authenticated', 'service_role', 'authenticator'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = api_role) THEN
            EXECUTE format('REVOKE ALL ON SCHEMA assistant FROM %I', api_role);
            EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA assistant FROM %I', api_role);
            EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA assistant FROM %I', api_role);
            EXECUTE format('REVOKE ALL ON ALL FUNCTIONS IN SCHEMA assistant FROM %I', api_role);
            EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA assistant REVOKE ALL ON TABLES FROM %I', api_role);
            EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA assistant REVOKE ALL ON SEQUENCES FROM %I', api_role);
            EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA assistant REVOKE ALL ON FUNCTIONS FROM %I', api_role);
        END IF;
    END LOOP;
END
$$;

REVOKE ALL ON SCHEMA assistant FROM campuscore_assistant_runtime;
REVOKE ALL ON ALL TABLES IN SCHEMA assistant FROM campuscore_assistant_runtime;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA assistant FROM campuscore_assistant_runtime;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA assistant FROM campuscore_assistant_runtime;
GRANT USAGE ON SCHEMA assistant TO campuscore_assistant_runtime;

GRANT SELECT, INSERT, DELETE ON assistant.chat_conversation TO campuscore_assistant_runtime;
GRANT UPDATE (title, state, updated_at, expires_at) ON assistant.chat_conversation TO campuscore_assistant_runtime;
GRANT SELECT, INSERT ON assistant.chat_message TO campuscore_assistant_runtime;
GRANT SELECT, INSERT ON assistant.chat_citation TO campuscore_assistant_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON assistant.chat_message_feedback TO campuscore_assistant_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON assistant.chat_turn_ledger TO campuscore_assistant_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON assistant.provider_dispatch_registry TO campuscore_assistant_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON assistant.usage_bucket TO campuscore_assistant_runtime;

GRANT SELECT, INSERT, UPDATE ON assistant.knowledge_document TO campuscore_assistant_runtime;
GRANT SELECT, INSERT, UPDATE ON assistant.knowledge_document_revision TO campuscore_assistant_runtime;
GRANT INSERT ON assistant.knowledge_document_audit TO campuscore_assistant_runtime;
GRANT SELECT, INSERT, UPDATE ON assistant.knowledge_release TO campuscore_assistant_runtime;
GRANT SELECT, INSERT ON assistant.knowledge_runtime_document TO campuscore_assistant_runtime;
GRANT SELECT, INSERT, UPDATE ON assistant.knowledge_runtime_state TO campuscore_assistant_runtime;

DO $$
DECLARE
    table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'chat_conversation',
        'chat_message',
        'chat_citation',
        'chat_message_feedback',
        'chat_turn_ledger',
        'provider_dispatch_registry',
        'usage_bucket',
        'knowledge_document',
        'knowledge_document_revision',
        'knowledge_document_audit',
        'knowledge_release',
        'knowledge_runtime_document',
        'knowledge_runtime_state'
    ] LOOP
        EXECUTE format('ALTER TABLE assistant.%I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE assistant.%I FORCE ROW LEVEL SECURITY', table_name);
    END LOOP;
END
$$;

-- Recreate only this migration's named policies. Other Assistant policies cause a safe failure above.
DROP POLICY IF EXISTS campuscore_assistant_conversation_owner ON assistant.chat_conversation;
CREATE POLICY campuscore_assistant_conversation_owner ON assistant.chat_conversation
    FOR ALL TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND owner_id = NULLIF(current_setting('app.assistant.owner_id', true), '')
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND owner_id = NULLIF(current_setting('app.assistant.owner_id', true), '')
    );
DROP POLICY IF EXISTS campuscore_assistant_conversation_retention_read ON assistant.chat_conversation;
CREATE POLICY campuscore_assistant_conversation_retention_read ON assistant.chat_conversation
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'RETENTION'
        AND expires_at <= CURRENT_TIMESTAMP
        AND state <> 'PURGED'
    );
DROP POLICY IF EXISTS campuscore_assistant_conversation_retention_update ON assistant.chat_conversation;
CREATE POLICY campuscore_assistant_conversation_retention_update ON assistant.chat_conversation
    FOR UPDATE TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'RETENTION'
        AND expires_at <= CURRENT_TIMESTAMP
        AND state <> 'PURGED'
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'RETENTION'
        AND expires_at <= CURRENT_TIMESTAMP
        AND state <> 'PURGED'
    );
DROP POLICY IF EXISTS campuscore_assistant_conversation_retention_delete ON assistant.chat_conversation;
CREATE POLICY campuscore_assistant_conversation_retention_delete ON assistant.chat_conversation
    FOR DELETE TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'RETENTION'
        AND expires_at <= CURRENT_TIMESTAMP
    );

DROP POLICY IF EXISTS campuscore_assistant_message_owner_read ON assistant.chat_message;
CREATE POLICY campuscore_assistant_message_owner_read ON assistant.chat_message
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND EXISTS (
            SELECT 1 FROM assistant.chat_conversation c
             WHERE c.id = conversation_id
               AND c.owner_id = NULLIF(current_setting('app.assistant.owner_id', true), '')
        )
    );
DROP POLICY IF EXISTS campuscore_assistant_message_owner_insert ON assistant.chat_message;
CREATE POLICY campuscore_assistant_message_owner_insert ON assistant.chat_message
    FOR INSERT TO campuscore_assistant_runtime
    WITH CHECK (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND EXISTS (
            SELECT 1 FROM assistant.chat_conversation c
             WHERE c.id = conversation_id
               AND c.owner_id = NULLIF(current_setting('app.assistant.owner_id', true), '')
        )
    );

DROP POLICY IF EXISTS campuscore_assistant_citation_owner_read ON assistant.chat_citation;
CREATE POLICY campuscore_assistant_citation_owner_read ON assistant.chat_citation
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND EXISTS (
            SELECT 1
              FROM assistant.chat_message m
              JOIN assistant.chat_conversation c ON c.id = m.conversation_id
             WHERE m.id = message_id
               AND c.owner_id = NULLIF(current_setting('app.assistant.owner_id', true), '')
        )
    );
DROP POLICY IF EXISTS campuscore_assistant_citation_owner_insert ON assistant.chat_citation;
CREATE POLICY campuscore_assistant_citation_owner_insert ON assistant.chat_citation
    FOR INSERT TO campuscore_assistant_runtime
    WITH CHECK (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND EXISTS (
            SELECT 1
              FROM assistant.chat_message m
              JOIN assistant.chat_conversation c ON c.id = m.conversation_id
             WHERE m.id = message_id
               AND c.owner_id = NULLIF(current_setting('app.assistant.owner_id', true), '')
        )
    );

DROP POLICY IF EXISTS campuscore_assistant_feedback_owner ON assistant.chat_message_feedback;
CREATE POLICY campuscore_assistant_feedback_owner ON assistant.chat_message_feedback
    FOR ALL TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND owner_id = NULLIF(current_setting('app.assistant.owner_id', true), '')
        AND EXISTS (
            SELECT 1
              FROM assistant.chat_message m
              JOIN assistant.chat_conversation c ON c.id = m.conversation_id
             WHERE m.id = message_id
               AND c.owner_id = chat_message_feedback.owner_id
        )
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND owner_id = NULLIF(current_setting('app.assistant.owner_id', true), '')
        AND EXISTS (
            SELECT 1
              FROM assistant.chat_message m
              JOIN assistant.chat_conversation c ON c.id = m.conversation_id
             WHERE m.id = message_id
               AND c.owner_id = chat_message_feedback.owner_id
        )
    );

DROP POLICY IF EXISTS campuscore_assistant_turn_owner ON assistant.chat_turn_ledger;
CREATE POLICY campuscore_assistant_turn_owner ON assistant.chat_turn_ledger
    FOR ALL TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND owner_id = NULLIF(current_setting('app.assistant.owner_id', true), '')
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND owner_id = NULLIF(current_setting('app.assistant.owner_id', true), '')
    );
DROP POLICY IF EXISTS campuscore_assistant_turn_retention_read ON assistant.chat_turn_ledger;
CREATE POLICY campuscore_assistant_turn_retention_read ON assistant.chat_turn_ledger
    FOR SELECT TO campuscore_assistant_runtime
    USING (current_setting('app.assistant.scope', true) = 'RETENTION');
DROP POLICY IF EXISTS campuscore_assistant_turn_retention_update ON assistant.chat_turn_ledger;
CREATE POLICY campuscore_assistant_turn_retention_update ON assistant.chat_turn_ledger
    FOR UPDATE TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'RETENTION'
        AND (
            (
                state IN ('RESERVED', 'SNAPSHOT_READY', 'DISPATCHED')
                AND lease_expires_at IS NOT NULL
                AND lease_expires_at <= CURRENT_TIMESTAMP
            )
            OR (
                state <> 'PURGED'
                AND conversation_id IN (
                    SELECT c.id FROM assistant.chat_conversation c
                     WHERE c.expires_at <= CURRENT_TIMESTAMP
                       AND c.state <> 'PURGED'
                )
            )
        )
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'RETENTION'
        AND (
            (state IN ('FAILED_PRE_DISPATCH', 'FAILED_AMBIGUOUS') AND lease_expires_at IS NULL)
            OR (state = 'PURGED' AND purged_at IS NOT NULL AND tombstone_until IS NOT NULL)
        )
    );
DROP POLICY IF EXISTS campuscore_assistant_turn_retention_delete ON assistant.chat_turn_ledger;
CREATE POLICY campuscore_assistant_turn_retention_delete ON assistant.chat_turn_ledger
    FOR DELETE TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'RETENTION'
        AND state = 'PURGED'
        AND tombstone_until < CURRENT_TIMESTAMP
        AND conversation_id IS NULL
    );

DROP POLICY IF EXISTS campuscore_assistant_dispatch_owner ON assistant.provider_dispatch_registry;
CREATE POLICY campuscore_assistant_dispatch_owner ON assistant.provider_dispatch_registry
    FOR ALL TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND owner_id = NULLIF(current_setting('app.assistant.owner_id', true), '')
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND owner_id = NULLIF(current_setting('app.assistant.owner_id', true), '')
    );
DROP POLICY IF EXISTS campuscore_assistant_dispatch_retention_read ON assistant.provider_dispatch_registry;
CREATE POLICY campuscore_assistant_dispatch_retention_read ON assistant.provider_dispatch_registry
    FOR SELECT TO campuscore_assistant_runtime
    USING (current_setting('app.assistant.scope', true) = 'RETENTION');
DROP POLICY IF EXISTS campuscore_assistant_dispatch_retention_update ON assistant.provider_dispatch_registry;
CREATE POLICY campuscore_assistant_dispatch_retention_update ON assistant.provider_dispatch_registry
    FOR UPDATE TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'RETENTION'
        AND state = 'DISPATCHED'
        AND EXISTS (
            SELECT 1
              FROM assistant.chat_turn_ledger l
             WHERE l.owner_id = provider_dispatch_registry.owner_id
               AND l.client_request_id = provider_dispatch_registry.client_request_id
               AND (
                    l.state = 'PURGED'
                    OR (l.state = 'FAILED_AMBIGUOUS' AND l.lease_generation = provider_dispatch_registry.lease_generation + 1)
               )
        )
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'RETENTION'
        AND state = 'CANCELLED'
    );
DROP POLICY IF EXISTS campuscore_assistant_dispatch_retention_delete ON assistant.provider_dispatch_registry;
CREATE POLICY campuscore_assistant_dispatch_retention_delete ON assistant.provider_dispatch_registry
    FOR DELETE TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'RETENTION'
        AND state <> 'DISPATCHED'
        AND NOT EXISTS (
            SELECT 1
              FROM assistant.chat_turn_ledger l
             WHERE l.owner_id = provider_dispatch_registry.owner_id
               AND l.client_request_id = provider_dispatch_registry.client_request_id
               AND l.lease_generation = provider_dispatch_registry.lease_generation
        )
    );

DROP POLICY IF EXISTS campuscore_assistant_usage_read ON assistant.usage_bucket;
CREATE POLICY campuscore_assistant_usage_read ON assistant.usage_bucket
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND (
            (owner_id = NULLIF(current_setting('app.assistant.owner_id', true), '') AND scope = 'USER')
            OR (owner_id = '*' AND scope = 'GLOBAL')
        )
    );
DROP POLICY IF EXISTS campuscore_assistant_usage_insert ON assistant.usage_bucket;
CREATE POLICY campuscore_assistant_usage_insert ON assistant.usage_bucket
    FOR INSERT TO campuscore_assistant_runtime
    WITH CHECK (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND (
            (owner_id = NULLIF(current_setting('app.assistant.owner_id', true), '') AND scope = 'USER')
            OR (owner_id = '*' AND scope = 'GLOBAL' AND request_count = 0)
        )
    );
DROP POLICY IF EXISTS campuscore_assistant_usage_update ON assistant.usage_bucket;
CREATE POLICY campuscore_assistant_usage_update ON assistant.usage_bucket
    FOR UPDATE TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND (
            (owner_id = NULLIF(current_setting('app.assistant.owner_id', true), '') AND scope = 'USER')
            OR (owner_id = '*' AND scope = 'GLOBAL')
        )
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND (
            (owner_id = NULLIF(current_setting('app.assistant.owner_id', true), '') AND scope = 'USER')
            OR (owner_id = '*' AND scope = 'GLOBAL')
        )
    );
DROP POLICY IF EXISTS campuscore_assistant_usage_delete_owner ON assistant.usage_bucket;
CREATE POLICY campuscore_assistant_usage_delete_owner ON assistant.usage_bucket
    FOR DELETE TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND owner_id = NULLIF(current_setting('app.assistant.owner_id', true), '')
        AND scope = 'USER'
    );
DROP POLICY IF EXISTS campuscore_assistant_usage_retention_read ON assistant.usage_bucket;
CREATE POLICY campuscore_assistant_usage_retention_read ON assistant.usage_bucket
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'RETENTION'
        AND bucket_date < CURRENT_DATE - 90
    );
DROP POLICY IF EXISTS campuscore_assistant_usage_retention_delete ON assistant.usage_bucket;
CREATE POLICY campuscore_assistant_usage_retention_delete ON assistant.usage_bucket
    FOR DELETE TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'RETENTION'
        AND bucket_date < CURRENT_DATE - 90
    );

DROP POLICY IF EXISTS campuscore_assistant_knowledge_document_admin ON assistant.knowledge_document;
CREATE POLICY campuscore_assistant_knowledge_document_admin ON assistant.knowledge_document
    FOR ALL TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'ADMIN_GOVERNANCE'
        AND current_setting('app.assistant.admin', true) = 'true'
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'ADMIN_GOVERNANCE'
        AND current_setting('app.assistant.admin', true) = 'true'
    );
DROP POLICY IF EXISTS campuscore_assistant_knowledge_revision_admin ON assistant.knowledge_document_revision;
CREATE POLICY campuscore_assistant_knowledge_revision_admin ON assistant.knowledge_document_revision
    FOR ALL TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'ADMIN_GOVERNANCE'
        AND current_setting('app.assistant.admin', true) = 'true'
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'ADMIN_GOVERNANCE'
        AND current_setting('app.assistant.admin', true) = 'true'
    );
DROP POLICY IF EXISTS campuscore_assistant_knowledge_audit_insert ON assistant.knowledge_document_audit;
CREATE POLICY campuscore_assistant_knowledge_audit_insert ON assistant.knowledge_document_audit
    FOR INSERT TO campuscore_assistant_runtime
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'ADMIN_GOVERNANCE'
        AND current_setting('app.assistant.admin', true) = 'true'
        AND actor_id = NULLIF(current_setting('app.assistant.owner_id', true), '')
    );

DROP POLICY IF EXISTS campuscore_assistant_release_admin_read ON assistant.knowledge_release;
CREATE POLICY campuscore_assistant_release_admin_read ON assistant.knowledge_release
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'ADMIN_GOVERNANCE'
        AND current_setting('app.assistant.admin', true) = 'true'
    );
DROP POLICY IF EXISTS campuscore_assistant_release_admin_insert ON assistant.knowledge_release;
CREATE POLICY campuscore_assistant_release_admin_insert ON assistant.knowledge_release
    FOR INSERT TO campuscore_assistant_runtime
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'ADMIN_GOVERNANCE'
        AND current_setting('app.assistant.admin', true) = 'true'
    );
DROP POLICY IF EXISTS campuscore_assistant_release_admin_update ON assistant.knowledge_release;
CREATE POLICY campuscore_assistant_release_admin_update ON assistant.knowledge_release
    FOR UPDATE TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'ADMIN_GOVERNANCE'
        AND current_setting('app.assistant.admin', true) = 'true'
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'ADMIN_GOVERNANCE'
        AND current_setting('app.assistant.admin', true) = 'true'
    );
DROP POLICY IF EXISTS campuscore_assistant_release_user_active_read ON assistant.knowledge_release;
CREATE POLICY campuscore_assistant_release_user_active_read ON assistant.knowledge_release
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND status = 'PUBLISHED'
        AND id = (
            SELECT s.active_release_id
              FROM assistant.knowledge_runtime_state s
             WHERE s.singleton = TRUE
        )
    );
DROP POLICY IF EXISTS campuscore_assistant_release_projection_read ON assistant.knowledge_release;
CREATE POLICY campuscore_assistant_release_projection_read ON assistant.knowledge_release
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'KNOWLEDGE_PROJECTION'
        AND (
            source = 'SUPABASE'
            OR id = (
                SELECT s.active_release_id
                  FROM assistant.knowledge_runtime_state s
                 WHERE s.singleton = TRUE
            )
        )
    );
DROP POLICY IF EXISTS campuscore_assistant_release_projection_insert ON assistant.knowledge_release;
CREATE POLICY campuscore_assistant_release_projection_insert ON assistant.knowledge_release
    FOR INSERT TO campuscore_assistant_runtime
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'KNOWLEDGE_PROJECTION'
        AND source = 'SUPABASE'
        AND status = 'STAGED'
    );
DROP POLICY IF EXISTS campuscore_assistant_release_projection_update ON assistant.knowledge_release;
CREATE POLICY campuscore_assistant_release_projection_update ON assistant.knowledge_release
    FOR UPDATE TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'KNOWLEDGE_PROJECTION'
        AND source = 'SUPABASE'
        AND status IN ('STAGED', 'PUBLISHED')
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'KNOWLEDGE_PROJECTION'
        AND source = 'SUPABASE'
        AND status IN ('STAGED', 'PUBLISHED')
    );

DROP POLICY IF EXISTS campuscore_assistant_runtime_document_user_read ON assistant.knowledge_runtime_document;
CREATE POLICY campuscore_assistant_runtime_document_user_read ON assistant.knowledge_runtime_document
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND active
        AND visibility = 'PUBLIC'
        AND EXISTS (
            SELECT 1
              FROM assistant.knowledge_runtime_state s
              JOIN assistant.knowledge_release r ON r.id = s.active_release_id
             WHERE s.singleton = TRUE
               AND s.active_release_id = knowledge_runtime_document.release_id
               AND r.status = 'PUBLISHED'
        )
    );
DROP POLICY IF EXISTS campuscore_assistant_runtime_document_admin_read ON assistant.knowledge_runtime_document;
CREATE POLICY campuscore_assistant_runtime_document_admin_read ON assistant.knowledge_runtime_document
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'ADMIN_GOVERNANCE'
        AND current_setting('app.assistant.admin', true) = 'true'
    );
DROP POLICY IF EXISTS campuscore_assistant_runtime_document_admin_insert ON assistant.knowledge_runtime_document;
CREATE POLICY campuscore_assistant_runtime_document_admin_insert ON assistant.knowledge_runtime_document
    FOR INSERT TO campuscore_assistant_runtime
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'ADMIN_GOVERNANCE'
        AND current_setting('app.assistant.admin', true) = 'true'
    );
DROP POLICY IF EXISTS campuscore_assistant_runtime_document_projection_read ON assistant.knowledge_runtime_document;
CREATE POLICY campuscore_assistant_runtime_document_projection_read ON assistant.knowledge_runtime_document
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'KNOWLEDGE_PROJECTION'
        AND EXISTS (
            SELECT 1 FROM assistant.knowledge_release r
             WHERE r.id = knowledge_runtime_document.release_id
               AND r.source = 'SUPABASE'
               AND r.status IN ('STAGED', 'PUBLISHED')
        )
    );
DROP POLICY IF EXISTS campuscore_assistant_runtime_document_projection_insert ON assistant.knowledge_runtime_document;
CREATE POLICY campuscore_assistant_runtime_document_projection_insert ON assistant.knowledge_runtime_document
    FOR INSERT TO campuscore_assistant_runtime
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'KNOWLEDGE_PROJECTION'
        AND visibility = 'PUBLIC'
        AND EXISTS (
            SELECT 1 FROM assistant.knowledge_release r
             WHERE r.id = release_id
               AND r.source = 'SUPABASE'
               AND r.status = 'STAGED'
        )
    );

DROP POLICY IF EXISTS campuscore_assistant_runtime_state_user_read ON assistant.knowledge_runtime_state;
CREATE POLICY campuscore_assistant_runtime_state_user_read ON assistant.knowledge_runtime_state
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) IN ('USER', 'INTERNAL_OWNER', 'ADMIN_GOVERNANCE')
        AND singleton = TRUE
    );
DROP POLICY IF EXISTS campuscore_assistant_runtime_state_admin_read ON assistant.knowledge_runtime_state;
CREATE POLICY campuscore_assistant_runtime_state_admin_read ON assistant.knowledge_runtime_state
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'ADMIN_GOVERNANCE'
        AND current_setting('app.assistant.admin', true) = 'true'
    );
DROP POLICY IF EXISTS campuscore_assistant_runtime_state_admin_insert ON assistant.knowledge_runtime_state;
CREATE POLICY campuscore_assistant_runtime_state_admin_insert ON assistant.knowledge_runtime_state
    FOR INSERT TO campuscore_assistant_runtime
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'ADMIN_GOVERNANCE'
        AND current_setting('app.assistant.admin', true) = 'true'
        AND singleton = TRUE
    );
DROP POLICY IF EXISTS campuscore_assistant_runtime_state_admin_update ON assistant.knowledge_runtime_state;
CREATE POLICY campuscore_assistant_runtime_state_admin_update ON assistant.knowledge_runtime_state
    FOR UPDATE TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'ADMIN_GOVERNANCE'
        AND current_setting('app.assistant.admin', true) = 'true'
        AND singleton = TRUE
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'ADMIN_GOVERNANCE'
        AND current_setting('app.assistant.admin', true) = 'true'
        AND singleton = TRUE
    );
DROP POLICY IF EXISTS campuscore_assistant_runtime_state_projection_read ON assistant.knowledge_runtime_state;
CREATE POLICY campuscore_assistant_runtime_state_projection_read ON assistant.knowledge_runtime_state
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'KNOWLEDGE_PROJECTION'
        AND singleton = TRUE
    );
DROP POLICY IF EXISTS campuscore_assistant_runtime_state_projection_insert ON assistant.knowledge_runtime_state;
CREATE POLICY campuscore_assistant_runtime_state_projection_insert ON assistant.knowledge_runtime_state
    FOR INSERT TO campuscore_assistant_runtime
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'KNOWLEDGE_PROJECTION'
        AND singleton = TRUE
        AND EXISTS (
            SELECT 1 FROM assistant.knowledge_release r
             WHERE r.id = active_release_id
               AND r.source = 'SUPABASE'
               AND r.status = 'PUBLISHED'
        )
    );
DROP POLICY IF EXISTS campuscore_assistant_runtime_state_projection_update ON assistant.knowledge_runtime_state;
CREATE POLICY campuscore_assistant_runtime_state_projection_update ON assistant.knowledge_runtime_state
    FOR UPDATE TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'KNOWLEDGE_PROJECTION'
        AND singleton = TRUE
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'KNOWLEDGE_PROJECTION'
        AND singleton = TRUE
        AND EXISTS (
            SELECT 1 FROM assistant.knowledge_release r
             WHERE r.id = active_release_id
               AND r.source = 'SUPABASE'
               AND r.status = 'PUBLISHED'
        )
    );
