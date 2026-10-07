-- Assistant CATALOG_SYNC scope: lets the scheduled PrerequisiteMapSyncJob
-- regenerate the catalog-prerequisite-map knowledge documents from live
-- academic rows. The job runs without an authenticated user, so it cannot
-- use ADMIN_GOVERNANCE; the KNOWLEDGE_PROJECTION policies are deliberately
-- locked to source='SUPABASE' and must stay that way.
--
-- Grants: the job reads academic."Course" / academic."CourseRequirement" to
-- rebuild the map bodies (those tables carry no RLS).
--
-- Policies: opened only while app.assistant.scope = 'CATALOG_SYNC', further
-- narrowed to the map document source / sync-authored rows wherever the job
-- writes.

GRANT USAGE ON SCHEMA academic TO campuscore_assistant_runtime;
GRANT SELECT ON academic."Course" TO campuscore_assistant_runtime;
GRANT SELECT ON academic."CourseRequirement" TO campuscore_assistant_runtime;

DROP POLICY IF EXISTS campuscore_assistant_knowledge_document_catalog_sync_read ON assistant.knowledge_document;
CREATE POLICY campuscore_assistant_knowledge_document_catalog_sync_read ON assistant.knowledge_document
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'CATALOG_SYNC'
        AND source = 'campuscore-prerequisite-map'
    );

DROP POLICY IF EXISTS campuscore_assistant_knowledge_document_catalog_sync_update ON assistant.knowledge_document;
CREATE POLICY campuscore_assistant_knowledge_document_catalog_sync_update ON assistant.knowledge_document
    FOR UPDATE TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'CATALOG_SYNC'
        AND source = 'campuscore-prerequisite-map'
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'CATALOG_SYNC'
        AND source = 'campuscore-prerequisite-map'
    );

DROP POLICY IF EXISTS campuscore_assistant_knowledge_revision_catalog_sync_read ON assistant.knowledge_document_revision;
CREATE POLICY campuscore_assistant_knowledge_revision_catalog_sync_read ON assistant.knowledge_document_revision
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'CATALOG_SYNC'
        AND EXISTS (
            SELECT 1 FROM assistant.knowledge_document d
             WHERE d.id = knowledge_document_revision.document_id
               AND d.source = 'campuscore-prerequisite-map'
        )
    );
DROP POLICY IF EXISTS campuscore_assistant_knowledge_revision_catalog_sync_insert ON assistant.knowledge_document_revision;
CREATE POLICY campuscore_assistant_knowledge_revision_catalog_sync_insert ON assistant.knowledge_document_revision
    FOR INSERT TO campuscore_assistant_runtime
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'CATALOG_SYNC'
        AND state = 'PUBLISHED'
        AND created_by = 'prerequisite-map-sync'
        AND EXISTS (
            SELECT 1 FROM assistant.knowledge_document d
             WHERE d.id = knowledge_document_revision.document_id
               AND d.source = 'campuscore-prerequisite-map'
        )
    );
DROP POLICY IF EXISTS campuscore_assistant_knowledge_revision_catalog_sync_update ON assistant.knowledge_document_revision;
CREATE POLICY campuscore_assistant_knowledge_revision_catalog_sync_update ON assistant.knowledge_document_revision
    FOR UPDATE TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'CATALOG_SYNC'
        AND EXISTS (
            SELECT 1 FROM assistant.knowledge_document d
             WHERE d.id = knowledge_document_revision.document_id
               AND d.source = 'campuscore-prerequisite-map'
        )
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'CATALOG_SYNC'
        AND EXISTS (
            SELECT 1 FROM assistant.knowledge_document d
             WHERE d.id = knowledge_document_revision.document_id
               AND d.source = 'campuscore-prerequisite-map'
        )
    );

DROP POLICY IF EXISTS campuscore_assistant_release_catalog_sync_read ON assistant.knowledge_release;
CREATE POLICY campuscore_assistant_release_catalog_sync_read ON assistant.knowledge_release
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'CATALOG_SYNC'
    );
DROP POLICY IF EXISTS campuscore_assistant_release_catalog_sync_insert ON assistant.knowledge_release;
CREATE POLICY campuscore_assistant_release_catalog_sync_insert ON assistant.knowledge_release
    FOR INSERT TO campuscore_assistant_runtime
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'CATALOG_SYNC'
        AND source = 'MANUAL'
        AND status = 'PUBLISHED'
        AND created_by = 'prerequisite-map-sync'
    );
DROP POLICY IF EXISTS campuscore_assistant_release_catalog_sync_update ON assistant.knowledge_release;
CREATE POLICY campuscore_assistant_release_catalog_sync_update ON assistant.knowledge_release
    FOR UPDATE TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'CATALOG_SYNC'
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'CATALOG_SYNC'
        AND source IN ('MANUAL', 'LEGACY')
    );

DROP POLICY IF EXISTS campuscore_assistant_runtime_document_catalog_sync_read ON assistant.knowledge_runtime_document;
CREATE POLICY campuscore_assistant_runtime_document_catalog_sync_read ON assistant.knowledge_runtime_document
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'CATALOG_SYNC'
    );
DROP POLICY IF EXISTS campuscore_assistant_runtime_document_catalog_sync_insert ON assistant.knowledge_runtime_document;
CREATE POLICY campuscore_assistant_runtime_document_catalog_sync_insert ON assistant.knowledge_runtime_document
    FOR INSERT TO campuscore_assistant_runtime
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'CATALOG_SYNC'
        AND visibility = 'PUBLIC'
        AND EXISTS (
            SELECT 1 FROM assistant.knowledge_release r
             WHERE r.id = knowledge_runtime_document.release_id
               AND r.source = 'MANUAL'
               AND r.status = 'PUBLISHED'
               AND r.created_by = 'prerequisite-map-sync'
        )
    );

DROP POLICY IF EXISTS campuscore_assistant_runtime_state_catalog_sync_read ON assistant.knowledge_runtime_state;
CREATE POLICY campuscore_assistant_runtime_state_catalog_sync_read ON assistant.knowledge_runtime_state
    FOR SELECT TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'CATALOG_SYNC'
        AND singleton = TRUE
    );
DROP POLICY IF EXISTS campuscore_assistant_runtime_state_catalog_sync_update ON assistant.knowledge_runtime_state;
CREATE POLICY campuscore_assistant_runtime_state_catalog_sync_update ON assistant.knowledge_runtime_state
    FOR UPDATE TO campuscore_assistant_runtime
    USING (
        current_setting('app.assistant.scope', true) = 'CATALOG_SYNC'
        AND singleton = TRUE
    )
    WITH CHECK (
        current_setting('app.assistant.scope', true) = 'CATALOG_SYNC'
        AND singleton = TRUE
    );
