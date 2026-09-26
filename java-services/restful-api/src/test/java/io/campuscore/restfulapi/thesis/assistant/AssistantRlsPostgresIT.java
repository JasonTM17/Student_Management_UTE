package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.campuscore.restfulapi.thesis.assistant.AssistantRlsBoundary.Access;
import io.campuscore.restfulapi.web.DomainException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.MockHttpServletResponse;

/** Real PostgreSQL RLS tests. The caller must supply a separate disposable database and runtime login. */
@EnabledIfEnvironmentVariable(named = "ASSISTANT_RLS_POSTGRES_URL", matches = "jdbc:postgresql:.+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.schemas=thesis,academic,campuscore_auth",
        "spring.flyway.default-schema=thesis",
        "spring.jpa.hibernate.ddl-auto=none",
        "assistant.rls.test-mode=false",
        "assistant.rag.service-mode=true",
        "assistant.rag.service-token=rls-it-internal-token-9fb407c5",
        "assistant.retention.enabled=false",
        "assistant.supabase.enabled=false",
        "deepseek.enabled=false",
        "spring.task.scheduling.enabled=false"
})
@ActiveProfiles("persistence")
class AssistantRlsPostgresIT {
    private static final String INTERNAL_TOKEN = "rls-it-internal-token-9fb407c5";

    @Autowired AssistantRlsTransactionRunner transactions;
    @Autowired ThesisAssistantRepository history;
    @Autowired ThesisAssistantTurnRepository turns;
    @Autowired ThesisAssistantInternalController internalController;
    @Autowired @Qualifier("namedParameterJdbcTemplate") NamedParameterJdbcTemplate primaryJdbc;
    @Autowired @Qualifier(AssistantDatabaseConfiguration.JDBC_TEMPLATE) NamedParameterJdbcTemplate assistantJdbc;
    @Autowired @Qualifier("dataSource") DataSource primaryDataSource;
    @Autowired @Qualifier(AssistantDatabaseConfiguration.DATA_SOURCE) DataSource assistantDataSource;

    private String ownerA;
    private String ownerB;
    private UUID conversationA;
    private UUID conversationB;
    private UUID messageA;
    private UUID messageB;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnv("ASSISTANT_RLS_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("ASSISTANT_RLS_PRIMARY_USER", "postgres"));
        registry.add("spring.datasource.password", () -> valueOr("ASSISTANT_RLS_PRIMARY_PASSWORD", ""));
        registry.add("assistant.datasource.url", () -> requiredEnv("ASSISTANT_RLS_POSTGRES_URL"));
        registry.add("assistant.datasource.username", () -> "campuscore_assistant_runtime");
        registry.add("assistant.datasource.password", () -> requiredEnv("ASSISTANT_RLS_RUNTIME_PASSWORD"));
        registry.add("assistant.datasource.maximum-pool-size", () -> "1");
        registry.add("assistant.rls.test-mode", () -> "false");
        registry.add("security.jwt.secret", () -> requiredEnv("ASSISTANT_RLS_JWT_SECRET"));
        registry.add("security.jwt.refresh-secret", () -> requiredEnv("ASSISTANT_RLS_JWT_REFRESH_SECRET"));
    }

    @BeforeEach
    void createTwoOwnerGraphs() throws Throwable {
        ownerA = "assistant-rls-a-" + UUID.randomUUID();
        ownerB = "assistant-rls-b-" + UUID.randomUUID();
        conversationA = UUID.randomUUID();
        conversationB = UUID.randomUUID();
        messageA = UUID.randomUUID();
        messageB = UUID.randomUUID();
        insertOwnerGraph(ownerA, conversationA, messageA);
        insertOwnerGraph(ownerB, conversationB, messageB);
    }

    @Test
    void runtimeLoginIsDedicatedAndMissingContextDefaultsToDeny() throws Throwable {
        assertFalse(primaryDataSource == assistantDataSource);
        assertEquals("postgres", scalar(primaryJdbc.getJdbcTemplate(), "SELECT current_user"));
        Map<String, Object> role = assistantJdbc.queryForMap("""
                SELECT current_user, session_user, r.rolcanlogin, r.rolsuper, r.rolcreatedb,
                       r.rolcreaterole, r.rolinherit, r.rolreplication, r.rolbypassrls,
                       r.rolconnlimit,
                       (SELECT count(*) FROM pg_auth_members m WHERE m.member=r.oid OR m.roleid=r.oid) memberships,
                       (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                         WHERE n.nspname='assistant' AND c.relkind IN ('r','p') AND c.relrowsecurity) rls_tables,
                       (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                         WHERE n.nspname='assistant' AND c.relkind IN ('r','p') AND c.relforcerowsecurity) forced_rls_tables,
                       (SELECT count(*) FROM pg_policies WHERE schemaname='assistant') policy_count,
                       COALESCE(current_setting('app.assistant.owner_id', true), '') owner_setting,
                       COALESCE(current_setting('app.assistant.scope', true), '') scope_setting,
                       COALESCE(current_setting('app.assistant.admin', true), '') admin_setting
                  FROM pg_roles r WHERE r.rolname=current_user
                """, Map.of());
        assertEquals("campuscore_assistant_runtime", role.get("current_user"));
        assertEquals(role.get("current_user"), role.get("session_user"));
        assertEquals(true, role.get("rolcanlogin"));
        assertEquals(false, role.get("rolsuper"));
        assertEquals(false, role.get("rolcreatedb"));
        assertEquals(false, role.get("rolcreaterole"));
        assertEquals(false, role.get("rolinherit"));
        assertEquals(false, role.get("rolreplication"));
        assertEquals(false, role.get("rolbypassrls"));
        assertEquals(8, ((Number) role.get("rolconnlimit")).intValue());
        assertEquals(0L, ((Number) role.get("memberships")).longValue());
        assertEquals(13L, ((Number) role.get("rls_tables")).longValue());
        assertEquals(13L, ((Number) role.get("forced_rls_tables")).longValue());
        assertEquals(45L, ((Number) role.get("policy_count")).longValue());
        assertEquals("", role.get("owner_setting"));
        assertEquals("", role.get("scope_setting"));
        assertEquals("", role.get("admin_setting"));
        assertEquals(0, count("SELECT COUNT(*) FROM assistant.chat_conversation"));
    }

    @Test
    void jwtOwnersAreIsolatedAcrossConversationChildrenAndQuotaLedger() throws Throwable {
        assertEquals(1, asUser(ownerA, "ROLE_STUDENT", () -> count("SELECT COUNT(*) FROM assistant.chat_conversation")));
        assertEquals(1, asUser(ownerA, "ROLE_STUDENT", () -> count("SELECT COUNT(*) FROM assistant.chat_message")));
        assertEquals(1, asUser(ownerA, "ROLE_STUDENT", () -> count("SELECT COUNT(*) FROM assistant.chat_citation")));
        assertEquals(1, asUser(ownerA, "ROLE_STUDENT", () -> count("SELECT COUNT(*) FROM assistant.chat_message_feedback")));
        assertEquals(1, asUser(ownerB, "ROLE_LECTURER", () -> count("SELECT COUNT(*) FROM assistant.chat_conversation")));
        assertEquals(0, asUser(ownerA, "ROLE_STUDENT", () -> count("SELECT COUNT(*) FROM assistant.chat_conversation WHERE id=:id",
                Map.of("id", conversationB))));
        assertEquals(0, asUser(ownerA, "ROLE_STUDENT", () -> assistantJdbc.update(
                "UPDATE assistant.chat_conversation SET title='cross-owner' WHERE id=:id", Map.of("id", conversationB))));
        assertEquals(0, asUser(ownerA, "ROLE_STUDENT", () -> assistantJdbc.update(
                "DELETE FROM assistant.chat_conversation WHERE id=:id", Map.of("id", conversationB))));

        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> asUser(ownerA, "ROLE_STUDENT", () -> {
                    insertConversation(ownerB, UUID.randomUUID());
                    return null;
                }));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> asUser(ownerA, "ROLE_STUDENT", () ->
                assistantJdbc.update("""
                        INSERT INTO assistant.chat_message(id,conversation_id,role,content,model,degraded,reason_code)
                        VALUES (:id,:conversation,'USER','forged parent','rls-it',FALSE,'ANSWERED')
                        """, Map.of("id", UUID.randomUUID(), "conversation", conversationB))));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> asUser(ownerA, "ROLE_STUDENT", () ->
                assistantJdbc.update("""
                        INSERT INTO assistant.chat_citation(id,message_id,slug,title,source,locale,excerpt)
                        VALUES (:id,:message,'forged','Forged','test','en','forged')
                        """, Map.of("id", UUID.randomUUID(), "message", messageB))));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> asUser(ownerA, "ROLE_STUDENT", () ->
                assistantJdbc.update("""
                        INSERT INTO assistant.chat_message_feedback(message_id,owner_id,rating,reason)
                        VALUES (:message,:owner,'UP','HELPFUL')
                        """, Map.of("message", messageB, "owner", ownerA))));

        UUID requestA = UUID.randomUUID();
        UUID requestB = UUID.randomUUID();
        var turnA = asUser(ownerA, "ROLE_STUDENT", () -> turns.reserve(ownerA, requestA, "a".repeat(64), null, "vi", "rls-it-a", 90));
        var turnB = asUser(ownerB, "ROLE_LECTURER", () -> turns.reserve(ownerB, requestB, "b".repeat(64), null, "en", "rls-it-b", 90));
        assertNotNull(turnA);
        assertNotNull(turnB);
        asUser(ownerA, "ROLE_STUDENT", () -> {
            assertTrue(turns.markSnapshotReady(turnA.turnId(), ownerA, turnA.leaseGeneration(), "a".repeat(64)));
            assertTrue(turns.dispatch(turnA.turnId(), ownerA, turnA.leaseGeneration(), 20, 200).dispatched());
            return null;
        });
        asUser(ownerB, "ROLE_LECTURER", () -> {
            assertTrue(turns.markSnapshotReady(turnB.turnId(), ownerB, turnB.leaseGeneration(), "b".repeat(64)));
            assertTrue(turns.dispatch(turnB.turnId(), ownerB, turnB.leaseGeneration(), 20, 200).dispatched());
            return null;
        });
        assertEquals(1, asUser(ownerA, "ROLE_STUDENT", () -> count("SELECT COUNT(*) FROM assistant.chat_turn_ledger")));
        assertEquals(1, asUser(ownerA, "ROLE_STUDENT", () -> count("SELECT COUNT(*) FROM assistant.provider_dispatch_registry")));
        assertEquals(1, asUser(ownerA, "ROLE_STUDENT", () -> count("SELECT COUNT(*) FROM assistant.usage_bucket WHERE scope='USER'")));
        assertEquals(1, asUser(ownerB, "ROLE_LECTURER", () -> count("SELECT COUNT(*) FROM assistant.chat_turn_ledger")));
        assertEquals(1, asUser(ownerB, "ROLE_LECTURER", () -> count("SELECT COUNT(*) FROM assistant.provider_dispatch_registry")));
        assertEquals(1, asUser(ownerB, "ROLE_LECTURER", () -> count("SELECT COUNT(*) FROM assistant.usage_bucket WHERE scope='USER'")));
        assertEquals(1, asUser(ownerA, "ROLE_STUDENT", () -> count(
                "SELECT COUNT(*) FROM assistant.usage_bucket WHERE owner_id='*' AND scope='GLOBAL'")));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> asUser(ownerA, "ROLE_STUDENT", () ->
                turns.reserve(ownerB, UUID.randomUUID(), "c".repeat(64), null, "vi", "forged-owner", 90)));
    }

    @Test
    void internalRagOwnerScopeIsEstablishedOnlyAfterServiceTokenValidation() throws Throwable {
        assertThrows(DomainException.class, () -> internalController.conversations(
                "invalid-token", ownerA, null, null, new MockHttpServletResponse()));
        var aRows = internalController.conversations(INTERNAL_TOKEN, ownerA, null, null, new MockHttpServletResponse());
        var bRows = internalController.conversations(INTERNAL_TOKEN, ownerB, null, null, new MockHttpServletResponse());
        assertEquals(List.of(conversationA), aRows.stream().map(ThesisAssistantRepository.Conversation::id).toList());
        assertEquals(List.of(conversationB), bRows.stream().map(ThesisAssistantRepository.Conversation::id).toList());
        assertThrows(DomainException.class, () -> internalController.conversations(
                INTERNAL_TOKEN, " ", null, null, new MockHttpServletResponse()));
        assertEquals(0, count("SELECT COUNT(*) FROM assistant.chat_conversation"));
    }

    @Test
    void adminGovernanceProjectionAndPublishedRuntimeHaveSeparatePolicies() throws Throwable {
        UUID documentId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        String adminOwner = "assistant-rls-admin-" + UUID.randomUUID();
        asAdmin(adminOwner, () -> {
            assistantJdbc.update("""
                    INSERT INTO assistant.knowledge_document
                        (id,slug,locale,title,content,source,domain,priority,active,visibility)
                    VALUES (:id,'rls-private','en','Private RLS test','not for students','internal','POLICY',10,TRUE,'PRIVATE')
                    """, Map.of("id", documentId));
            assistantJdbc.update("""
                    INSERT INTO assistant.knowledge_document_revision
                        (id,document_id,version,state,locale,slug,title,content,source,priority,created_by,domain)
                    VALUES (:id,:document,1,'DRAFT','en','rls-private','Private RLS test','draft','internal',10,:actor,'POLICY')
                    """, Map.of("id", revisionId, "document", documentId, "actor", adminOwner));
            assistantJdbc.update("""
                    INSERT INTO assistant.knowledge_document_audit(id,revision_id,action,actor_id,note)
                    VALUES (:id,:revision,'CREATED',:actor,'RLS integration fixture')
                    """, Map.of("id", UUID.randomUUID(), "revision", revisionId, "actor", adminOwner));
            return null;
        });
        assertEquals(1, asAdmin(adminOwner, () -> count("SELECT COUNT(*) FROM assistant.knowledge_document WHERE id=:id",
                Map.of("id", documentId))));
        assertEquals(1, asAdmin(adminOwner, () -> count("SELECT COUNT(*) FROM assistant.knowledge_document_revision WHERE id=:id",
                Map.of("id", revisionId))));
        assertEquals(1, scalarCount(primaryJdbc.getJdbcTemplate(),
                "SELECT COUNT(*) FROM assistant.knowledge_document_audit WHERE revision_id='" + revisionId + "'"));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> asAdmin(adminOwner,
                () -> count("SELECT COUNT(*) FROM assistant.knowledge_document_audit WHERE revision_id=:id",
                        Map.of("id", revisionId))));
        assertEquals(0, asUser(ownerA, "ROLE_STUDENT", () -> count("SELECT COUNT(*) FROM assistant.knowledge_document WHERE id=:id",
                Map.of("id", documentId))));
        assertEquals(0, asUser(ownerB, "ROLE_LECTURER", () -> count("SELECT COUNT(*) FROM assistant.knowledge_document_revision WHERE id=:id",
                Map.of("id", revisionId))));
        assertThrows(IllegalStateException.class, () -> asUser(ownerA, "ROLE_STUDENT",
                () -> transactions.execute(Access.ADMIN_GOVERNANCE, () -> count("SELECT COUNT(*) FROM assistant.knowledge_document"))));
        assertThrows(IllegalStateException.class, () -> asUser(ownerB, "ROLE_LECTURER",
                () -> transactions.execute(Access.ADMIN_GOVERNANCE, () -> count("SELECT COUNT(*) FROM assistant.knowledge_document"))));

        UUID olderRelease = UUID.randomUUID();
        UUID stagedRelease = UUID.randomUUID();
        seedPublishedRelease(olderRelease, "1".repeat(64), "old-public");
        primaryJdbc.update("""
                INSERT INTO assistant.knowledge_release(id,corpus_version,corpus_hash,row_count,source,status,manifest,created_by)
                VALUES (:id,'rls-projection-staged',:hash,1,'SUPABASE','STAGED','{}'::jsonb,'rls-it-sync')
                """, Map.of("id", stagedRelease, "hash", "2".repeat(64)));
        primaryJdbc.update("INSERT INTO assistant.knowledge_runtime_state(singleton,active_release_id) VALUES (TRUE,:id) ON CONFLICT (singleton) DO UPDATE SET active_release_id=EXCLUDED.active_release_id,updated_at=CURRENT_TIMESTAMP",
                Map.of("id", olderRelease));

        system(Access.KNOWLEDGE_PROJECTION, () -> {
            assistantJdbc.update("""
                    INSERT INTO assistant.knowledge_runtime_document
                        (release_id,source_id,version,domain,slug,locale,title,content,source,priority,active,visibility,published_at)
                    VALUES (:release,'new-public',1,'POLICY','new-public','en','New public policy','approved content','supabase',10,TRUE,'PUBLIC',CURRENT_TIMESTAMP)
                    """, Map.of("release", stagedRelease));
            assertEquals(1, assistantJdbc.update("UPDATE assistant.knowledge_release SET status='PUBLISHED',activated_at=CURRENT_TIMESTAMP,previous_release_id=:previous WHERE id=:id AND status='STAGED'",
                    Map.of("id", stagedRelease, "previous", olderRelease)));
            assertEquals(1, assistantJdbc.update("UPDATE assistant.knowledge_runtime_state SET active_release_id=:id,updated_at=CURRENT_TIMESTAMP WHERE singleton=TRUE",
                    Map.of("id", stagedRelease)));
            return null;
        });
        assertEquals(1, asUser(ownerA, "ROLE_STUDENT", () -> count("SELECT COUNT(*) FROM assistant.knowledge_runtime_document")));
        assertEquals("new-public", asUser(ownerA, "ROLE_STUDENT", () -> assistantJdbc.queryForObject(
                "SELECT source_id FROM assistant.knowledge_runtime_document", Map.of(), String.class)));
        assertEquals(1, asUser(ownerA, "ROLE_STUDENT", () -> count("SELECT COUNT(*) FROM assistant.knowledge_release")));
        assertEquals(0, asUser(ownerA, "ROLE_STUDENT", () -> count("SELECT COUNT(*) FROM assistant.knowledge_document")));
        assertEquals(2, system(Access.KNOWLEDGE_PROJECTION, () -> count("SELECT COUNT(*) FROM assistant.knowledge_release")));
        assertEquals(0, system(Access.KNOWLEDGE_PROJECTION, () -> count("SELECT COUNT(*) FROM assistant.knowledge_document")));
        assertEquals(0, system(Access.RETENTION, () -> count("SELECT COUNT(*) FROM assistant.knowledge_release")));
        assertThrows(DataAccessException.class, () -> system(Access.KNOWLEDGE_PROJECTION,
                () -> assistantJdbc.update("UPDATE assistant.knowledge_release SET status='ARCHIVED' WHERE id=:id",
                        Map.of("id", olderRelease))));
    }

    @Test
    void retentionRecoversExpiredLeasesAndPurgesOnlyExpiredHistory() throws Throwable {
        UUID request = UUID.randomUUID();
        var reservation = asUser(ownerA, "ROLE_STUDENT", () -> turns.reserve(ownerA, request, "d".repeat(64), null, "vi", "rls-it-retention", 90));
        asUser(ownerA, "ROLE_STUDENT", () -> {
            assertTrue(turns.markSnapshotReady(reservation.turnId(), ownerA, reservation.leaseGeneration(), "e".repeat(64)));
            assertTrue(turns.dispatch(reservation.turnId(), ownerA, reservation.leaseGeneration(), 20, 200).dispatched());
            return null;
        });
        primaryJdbc.update("UPDATE assistant.chat_turn_ledger SET lease_expires_at=CURRENT_TIMESTAMP-INTERVAL '1 minute' WHERE turn_id=:id",
                Map.of("id", reservation.turnId()));
        var recovered = turns.recoverExpiredLeases();
        assertTrue(recovered.stream().anyMatch(lease -> lease.turnId().equals(reservation.turnId())
                && lease.terminalState().equals("FAILED_AMBIGUOUS")));
        assertEquals("FAILED_AMBIGUOUS", asUser(ownerA, "ROLE_STUDENT", () -> assistantJdbc.queryForObject(
                "SELECT state FROM assistant.chat_turn_ledger WHERE turn_id=:id", Map.of("id", reservation.turnId()), String.class)));
        assertEquals("CANCELLED", asUser(ownerA, "ROLE_STUDENT", () -> assistantJdbc.queryForObject(
                "SELECT state FROM assistant.provider_dispatch_registry WHERE owner_id=:owner AND client_request_id=:request",
                Map.of("owner", ownerA, "request", request), String.class)));

        UUID expiredConversation = UUID.randomUUID();
        UUID expiredMessage = UUID.randomUUID();
        primaryJdbc.update("INSERT INTO assistant.chat_conversation(id,owner_id,locale,state,expires_at) VALUES (:id,:owner,'en','ACTIVE',CURRENT_TIMESTAMP-INTERVAL '2 minutes')",
                Map.of("id", expiredConversation, "owner", ownerB));
        primaryJdbc.update("""
                INSERT INTO assistant.chat_message(id,conversation_id,role,content,model,degraded,reason_code)
                VALUES (:id,:conversation,'USER','expired RLS fixture','rls-it',FALSE,'ANSWERED')
                """, Map.of("id", expiredMessage, "conversation", expiredConversation));
        primaryJdbc.update("INSERT INTO assistant.chat_citation(id,message_id,slug,title,source,locale,excerpt) VALUES (:id,:message,'expired','Expired fixture','test','en','expired')",
                Map.of("id", UUID.randomUUID(), "message", expiredMessage));
        primaryJdbc.update("INSERT INTO assistant.chat_message_feedback(message_id,owner_id,rating,reason) VALUES (:message,:owner,'DOWN','OUTDATED')",
                Map.of("message", expiredMessage, "owner", ownerB));
        assertEquals(1, system(Access.RETENTION, () -> count("SELECT COUNT(*) FROM assistant.chat_conversation WHERE id=:id",
                Map.of("id", expiredConversation))));
        assertEquals(0, asUser(ownerA, "ROLE_STUDENT", () -> assistantJdbc.update("DELETE FROM assistant.chat_conversation WHERE id=:id",
                Map.of("id", expiredConversation))));
        var retentionCandidates = system(Access.RETENTION, () -> assistantJdbc.query(
                "SELECT id FROM assistant.chat_conversation WHERE expires_at<=CURRENT_TIMESTAMP AND state<>'PURGED' ORDER BY expires_at LIMIT 100 FOR UPDATE",
                Map.of(), (rs, ignored) -> rs.getObject("id", UUID.class)));
        assertTrue(retentionCandidates.contains(expiredConversation),
                "retention candidate query omitted fixture: " + retentionCandidates);
        assertFalse(system(Access.RETENTION, () -> assistantJdbc.query(
                "SELECT id FROM assistant.chat_conversation WHERE id=:id FOR UPDATE",
                Map.of("id", conversationA), (rs, ignored) -> rs.getObject("id", UUID.class)).contains(conversationA)),
                "retention scope must not lock unexpired conversations");
        var purged = turns.purgeExpiredConversations();
        assertTrue(purged.stream().anyMatch(row -> row.id().equals(expiredConversation)),
                "retention result did not include expired fixture: " + purged);
        assertEquals(0, scalarCount(primaryJdbc.getJdbcTemplate(), "SELECT COUNT(*) FROM assistant.chat_conversation WHERE id='" + expiredConversation + "'"));
        assertEquals(0, scalarCount(primaryJdbc.getJdbcTemplate(), "SELECT COUNT(*) FROM assistant.chat_message WHERE id='" + expiredMessage + "'"));
        assertEquals(0, scalarCount(primaryJdbc.getJdbcTemplate(), "SELECT COUNT(*) FROM assistant.chat_citation WHERE message_id='" + expiredMessage + "'"));
        assertEquals(0, scalarCount(primaryJdbc.getJdbcTemplate(), "SELECT COUNT(*) FROM assistant.chat_message_feedback WHERE message_id='" + expiredMessage + "'"));
        assertEquals(1, asUser(ownerA, "ROLE_STUDENT", () -> count("SELECT COUNT(*) FROM assistant.chat_conversation WHERE id=:id",
                Map.of("id", conversationA))));
    }

    @Test
    void deleteCascadesOnlyThroughTheOwningConversationAndApiRolesHaveNoAssistantAcl() throws Throwable {
        assertEquals(1, asUser(ownerA, "ROLE_STUDENT", () -> history.deleteConversation(conversationA, ownerA)));
        assertEquals(0, asUser(ownerA, "ROLE_STUDENT", () -> count("SELECT COUNT(*) FROM assistant.chat_message")));
        assertEquals(0, asUser(ownerA, "ROLE_STUDENT", () -> count("SELECT COUNT(*) FROM assistant.chat_citation")));
        assertEquals(0, asUser(ownerA, "ROLE_STUDENT", () -> count("SELECT COUNT(*) FROM assistant.chat_message_feedback")));
        assertEquals(1, asUser(ownerB, "ROLE_LECTURER", () -> count("SELECT COUNT(*) FROM assistant.chat_message")));
        Integer apiRolesWithAccess = primaryJdbc.getJdbcTemplate().queryForObject("""
                SELECT count(*) FROM pg_roles a
                 WHERE a.rolname = ANY (ARRAY['anon','authenticated','service_role','authenticator'])
                   AND (has_schema_privilege(a.oid,'assistant','USAGE')
                     OR has_schema_privilege(a.oid,'assistant','CREATE')
                     OR EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                                 WHERE n.nspname='assistant' AND c.relkind IN ('r','p','v','m','f')
                                   AND (has_table_privilege(a.oid,c.oid,'SELECT') OR has_table_privilege(a.oid,c.oid,'INSERT')
                                     OR has_table_privilege(a.oid,c.oid,'UPDATE') OR has_table_privilege(a.oid,c.oid,'DELETE')
                                     OR has_table_privilege(a.oid,c.oid,'TRUNCATE') OR has_table_privilege(a.oid,c.oid,'REFERENCES')
                                     OR has_table_privilege(a.oid,c.oid,'TRIGGER')))
                     OR EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                                 WHERE n.nspname='assistant' AND c.relkind='S'
                                   AND (has_sequence_privilege(a.oid,c.oid,'USAGE') OR has_sequence_privilege(a.oid,c.oid,'SELECT')
                                     OR has_sequence_privilege(a.oid,c.oid,'UPDATE')))
                     OR EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                                 WHERE n.nspname='assistant' AND has_function_privilege(a.oid,p.oid,'EXECUTE')))
                """, Integer.class);
        assertEquals(0, apiRolesWithAccess);
    }

    @Test
    void transactionLocalRlsContextDoesNotLeakAfterCommitOrRollbackOnReusedConnection() throws Throwable {
        int backendPid = scalarInt(assistantJdbc, "SELECT pg_backend_pid()");
        assertEquals("", scalar(assistantJdbc,
                "SELECT COALESCE(current_setting('app.assistant.owner_id', true), '')"));

        assertEquals(ownerA, asUser(ownerA, "ROLE_STUDENT", () -> {
            assertEquals(ownerA, scalar(assistantJdbc,
                    "SELECT current_setting('app.assistant.owner_id', true)"));
            assertEquals("USER", scalar(assistantJdbc,
                    "SELECT current_setting('app.assistant.scope', true)"));
            return scalar(assistantJdbc, "SELECT current_setting('app.assistant.owner_id', true)");
        }));
        assertEquals(backendPid, scalarInt(assistantJdbc, "SELECT pg_backend_pid()"));
        assertEquals("", scalar(assistantJdbc,
                "SELECT COALESCE(current_setting('app.assistant.owner_id', true), '')"));
        assertEquals("", scalar(assistantJdbc,
                "SELECT COALESCE(current_setting('app.assistant.scope', true), '')"));
        assertEquals("", scalar(assistantJdbc,
                "SELECT COALESCE(current_setting('app.assistant.admin', true), '')"));

        assertThrows(IllegalArgumentException.class, () -> asUser(ownerB, "ROLE_LECTURER", () -> {
            assertEquals(ownerB, scalar(assistantJdbc,
                    "SELECT current_setting('app.assistant.owner_id', true)"));
            throw new IllegalArgumentException("force rollback to verify transaction-local cleanup");
        }));
        assertEquals(backendPid, scalarInt(assistantJdbc, "SELECT pg_backend_pid()"));
        assertEquals("", scalar(assistantJdbc,
                "SELECT COALESCE(current_setting('app.assistant.owner_id', true), '')"));
        assertEquals("", scalar(assistantJdbc,
                "SELECT COALESCE(current_setting('app.assistant.scope', true), '')"));
        assertEquals("", scalar(assistantJdbc,
                "SELECT COALESCE(current_setting('app.assistant.admin', true), '')"));
    }

    private void insertOwnerGraph(String owner, UUID conversation, UUID message) throws Throwable {
        asUser(owner, "ROLE_STUDENT", () -> {
            insertConversation(owner, conversation);
            assistantJdbc.update("""
                    INSERT INTO assistant.chat_message(id,conversation_id,role,content,model,degraded,reason_code)
                    VALUES (:id,:conversation,'USER','owner-private fixture','rls-it',FALSE,'ANSWERED')
                    """, Map.of("id", message, "conversation", conversation));
            assistantJdbc.update("""
                    INSERT INTO assistant.chat_citation(id,message_id,slug,title,source,locale,excerpt)
                    VALUES (:id,:message,'owner-source','Owner source','test','en','owner-only excerpt')
                    """, Map.of("id", UUID.randomUUID(), "message", message));
            assistantJdbc.update("INSERT INTO assistant.chat_message_feedback(message_id,owner_id,rating,reason) VALUES (:message,:owner,'UP','HELPFUL')",
                    Map.of("message", message, "owner", owner));
            return null;
        });
    }

    private void insertConversation(String owner, UUID id) {
        assistantJdbc.update("""
                INSERT INTO assistant.chat_conversation(id,owner_id,locale,state,expires_at)
                VALUES (:id,:owner,'en','ACTIVE',CURRENT_TIMESTAMP+INTERVAL '30 days')
                """, Map.of("id", id, "owner", owner));
    }

    private void seedPublishedRelease(UUID release, String hash, String sourceId) {
        primaryJdbc.update("""
                INSERT INTO assistant.knowledge_release(id,corpus_version,corpus_hash,row_count,source,status,manifest,created_by,activated_at)
                VALUES (:id,'rls-it-old',:hash,1,'SUPABASE','PUBLISHED','{}'::jsonb,'rls-it-seed',CURRENT_TIMESTAMP)
                """, Map.of("id", release, "hash", hash));
        insertRuntimeDocument(release, sourceId);
    }

    private void insertRuntimeDocument(UUID release, String sourceId) {
        primaryJdbc.update("""
                INSERT INTO assistant.knowledge_runtime_document
                    (release_id,source_id,version,domain,slug,locale,title,content,source,priority,active,visibility,published_at)
                VALUES (:release,:sourceId,1,'POLICY',:sourceId,'en',:title,'approved content','supabase',10,TRUE,'PUBLIC',CURRENT_TIMESTAMP)
                """, Map.of("release", release, "sourceId", sourceId, "title", sourceId));
    }

    private <T> T asUser(String owner, String role, AssistantRlsTransactionRunner.Work<T> work) throws Throwable {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Jwt jwt = Jwt.withTokenValue("rls-it-token").header("alg", "none").subject(owner).build();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role))));
        SecurityContextHolder.setContext(context);
        try {
            return transactions.execute(Access.AUTO, work);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private <T> T asAdmin(String owner, AssistantRlsTransactionRunner.Work<T> work) throws Throwable {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Jwt jwt = Jwt.withTokenValue("rls-it-admin-token").header("alg", "none").subject(owner).build();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(context);
        try {
            return transactions.execute(Access.ADMIN_GOVERNANCE, work);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private <T> T system(Access access, AssistantRlsTransactionRunner.Work<T> work) throws Throwable {
        return transactions.execute(access, work);
    }

    private int count(String sql) {
        return count(sql, Map.of());
    }

    private int count(String sql, Map<String, ?> parameters) {
        Integer value = assistantJdbc.queryForObject(sql, parameters, Integer.class);
        return value == null ? 0 : value;
    }

    private static String scalar(NamedParameterJdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Map.of(), String.class);
    }

    private static int scalarInt(NamedParameterJdbcTemplate jdbc, String sql) {
        Integer value = jdbc.queryForObject(sql, Map.of(), Integer.class);
        return value == null ? 0 : value;
    }

    private static String scalar(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, String.class);
    }

    private static int scalarCount(JdbcTemplate jdbc, String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for PostgreSQL RLS integration tests");
        return value;
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
