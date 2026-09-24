package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.campuscore.restfulapi.web.DomainException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionTemplate;

class SqlKnowledgeReleasePromoterTest {
    private JdbcTemplate raw;
    private ThesisAssistantKnowledgeAdminController controller;
    private TransactionTemplate transactions;
    private UUID initialRelease;

    @BeforeEach
    void setUp() {
        DataSource dataSource;
        String postgresUrl = System.getenv("ASSISTANT_POSTGRES_URL");
        if (postgresUrl == null || postgresUrl.isBlank()) {
            JdbcDataSource h2 = new JdbcDataSource();
            h2.setURL("jdbc:h2:mem:manual_release_" + UUID.randomUUID()
                    + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            dataSource = h2;
        } else {
            if (!"1".equals(System.getenv("ASSISTANT_POSTGRES_EPHEMERAL"))
                    || !postgresUrl.matches("jdbc:postgresql://127\\.0\\.0\\.1:5433/campuscore_restful_e2e")) {
                throw new IllegalStateException("PostgreSQL authority test requires the named disposable E2E database");
            }
            DriverManagerDataSource postgres = new DriverManagerDataSource();
            postgres.setDriverClassName("org.postgresql.Driver");
            postgres.setUrl(postgresUrl);
            postgres.setUsername("campuscore");
            postgres.setPassword("campuscore_password");
            dataSource = postgres;
        }
        raw = new JdbcTemplate(dataSource);
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        if (postgresUrl != null && !postgresUrl.isBlank()) raw.execute("DROP SCHEMA IF EXISTS assistant CASCADE");
        raw.execute("CREATE SCHEMA assistant");
        raw.execute("CREATE TABLE assistant.knowledge_document (id UUID PRIMARY KEY, slug VARCHAR(180) NOT NULL, locale VARCHAR(8) NOT NULL, title VARCHAR(500) NOT NULL, content TEXT NOT NULL, source VARCHAR(240) NOT NULL, domain VARCHAR(48) NOT NULL, priority SMALLINT NOT NULL, active BOOLEAN NOT NULL, visibility VARCHAR(32) NOT NULL, archived_at TIMESTAMP, archived_by VARCHAR(120), updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        raw.execute("CREATE TABLE assistant.knowledge_document_revision (id UUID PRIMARY KEY, document_id UUID NOT NULL, version INTEGER NOT NULL, state VARCHAR(24) NOT NULL, domain VARCHAR(48) NOT NULL, locale VARCHAR(8) NOT NULL, slug VARCHAR(180) NOT NULL, title VARCHAR(500) NOT NULL, content TEXT NOT NULL, source VARCHAR(240) NOT NULL, priority SMALLINT NOT NULL, created_by VARCHAR(120) NOT NULL, reviewed_by VARCHAR(120), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, published_at TIMESTAMP)");
        raw.execute("CREATE TABLE assistant.knowledge_document_audit (id UUID PRIMARY KEY, revision_id UUID NOT NULL, action VARCHAR(24) NOT NULL, actor_id VARCHAR(120) NOT NULL)");
        raw.execute("CREATE TABLE assistant.knowledge_release (id UUID PRIMARY KEY, corpus_version VARCHAR(120) NOT NULL, corpus_hash CHAR(64) NOT NULL UNIQUE, row_count INTEGER NOT NULL, source VARCHAR(24) NOT NULL, status VARCHAR(24) NOT NULL, manifest JSON, created_by VARCHAR(120) NOT NULL, activated_at TIMESTAMP WITH TIME ZONE, previous_release_id UUID)");
        raw.execute("CREATE TABLE assistant.knowledge_runtime_document (release_id UUID NOT NULL, source_id VARCHAR(180) NOT NULL, revision_id UUID, version INTEGER NOT NULL, domain VARCHAR(48) NOT NULL, slug VARCHAR(180) NOT NULL, locale VARCHAR(8) NOT NULL, title VARCHAR(500) NOT NULL, content TEXT NOT NULL, source VARCHAR(240) NOT NULL, priority SMALLINT NOT NULL, active BOOLEAN NOT NULL, visibility VARCHAR(32) NOT NULL, published_at TIMESTAMP WITH TIME ZONE NOT NULL, PRIMARY KEY (release_id,source_id))");
        raw.execute("CREATE TABLE assistant.knowledge_runtime_state (singleton BOOLEAN PRIMARY KEY, active_release_id UUID, updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)");
        initialRelease = UUID.randomUUID();
        raw.update("INSERT INTO assistant.knowledge_release (id,corpus_version,corpus_hash,row_count,source,status,manifest,created_by) VALUES (?,?,?,?,?,?,JSON '{}',?)",
                initialRelease, "legacy", "a".repeat(64), 0, "LEGACY", "PUBLISHED", "seed");
        raw.update("INSERT INTO assistant.knowledge_runtime_state (singleton,active_release_id) VALUES(TRUE,?)", initialRelease);
        controller = new ThesisAssistantKnowledgeAdminController(jdbc, new SqlKnowledgeReleasePromoter(jdbc, false));
    }

    @Test
    void secondAdminPublishAndArchiveSwitchOnlyTheActiveImmutableRelease() {
        var request = new ThesisAssistantKnowledgeAdminController.KnowledgeRequest(
                "test-registration-rule", "en", "Registration rule", "Public registration guidance", "academic office", 10, "REGISTRATION");
        UUID document = tx(() -> controller.create(request, actor("admin-a"))).documentId();
        tx(() -> controller.submit(document, actor("admin-a")));
        assertThrows(DomainException.class, () -> tx(() -> controller.publish(document, actor("admin-a"))));
        assertEquals(initialRelease, activeRelease());

        tx(() -> controller.publish(document, actor("admin-b")));
        UUID publishedRelease = activeRelease();
        assertNotEquals(initialRelease, publishedRelease);
        assertEquals(1, raw.queryForObject("SELECT row_count FROM assistant.knowledge_release WHERE id=?", Integer.class, publishedRelease));
        assertEquals("REGISTRATION", raw.queryForObject(
                "SELECT domain FROM assistant.knowledge_runtime_document WHERE release_id=? AND source_id=?",
                String.class, publishedRelease, document.toString()));
        assertEquals(0, raw.queryForObject("SELECT COUNT(*) FROM assistant.knowledge_runtime_document WHERE release_id=?", Integer.class, initialRelease));

        tx(() -> { controller.delete(document, actor("admin-b")); return null; });
        UUID archivedRelease = activeRelease();
        assertNotEquals(publishedRelease, archivedRelease);
        assertEquals(0, raw.queryForObject("SELECT row_count FROM assistant.knowledge_release WHERE id=?", Integer.class, archivedRelease));
        assertEquals(1, raw.queryForObject("SELECT COUNT(*) FROM assistant.knowledge_runtime_document WHERE release_id=?", Integer.class, publishedRelease));
    }

    @Test
    void supabaseOwnedPointerRejectsLocalPublishAndRollsBackAuthoring() {
        raw.update("UPDATE assistant.knowledge_release SET source='SUPABASE' WHERE id=?", initialRelease);
        var request = new ThesisAssistantKnowledgeAdminController.KnowledgeRequest(
                "test-authority-boundary", "en", "Public notice", "Public semester notice", "academic office", 10, "ANNOUNCEMENT");
        UUID document = tx(() -> controller.create(request, actor("admin-a"))).documentId();
        tx(() -> controller.submit(document, actor("admin-a")));

        assertThrows(DomainException.class, () -> tx(() -> controller.publish(document, actor("admin-b"))));
        assertEquals(initialRelease, activeRelease());
        assertEquals("PENDING_REVIEW", raw.queryForObject(
                "SELECT state FROM assistant.knowledge_document_revision WHERE document_id=?", String.class, document));
        assertEquals(0, raw.queryForObject("SELECT COUNT(*) FROM assistant.knowledge_document_audit WHERE action='PUBLISH'", Integer.class));
    }

    @Test
    void archivedDocumentRejectsPublishWithConflict() {
        var request = new ThesisAssistantKnowledgeAdminController.KnowledgeRequest(
                "test-archive-resurrection", "en", "Archive rule", "Public archive guidance", "academic office", 10, "REGISTRATION");
        UUID document = tx(() -> controller.create(request, actor("admin-a"))).documentId();
        tx(() -> controller.submit(document, actor("admin-a")));

        // Archive the document
        tx(() -> { controller.delete(document, actor("admin-b")); return null; });
        assertEquals(Boolean.FALSE, raw.queryForObject("SELECT active FROM assistant.knowledge_document WHERE id=?", Boolean.class, document));

        // Attempting to publish an archived document must fail with 409 KNOWLEDGE_STATE_CONFLICT
        DomainException ex = assertThrows(DomainException.class, () -> tx(() -> controller.publish(document, actor("admin-b"))));
        assertEquals("KNOWLEDGE_STATE_CONFLICT", ex.getCode());
    }

    @Test
    void listProjectionOmitsContentWhileGetIncludesIt() {
        var request = new ThesisAssistantKnowledgeAdminController.KnowledgeRequest(
                "test-list-projection", "en", "Projection test", "Detailed guidance body content", "academic office", 10, "REGISTRATION");
        UUID document = tx(() -> controller.create(request, actor("admin-a"))).documentId();

        var listRows = controller.list("REGISTRATION", null);
        var found = listRows.stream().filter(r -> r.documentId().equals(document)).findFirst().orElseThrow();
        assertEquals("", found.content());

        var detail = controller.get(document);
        assertEquals("Detailed guidance body content", detail.content());
    }

    private UUID activeRelease() {
        return raw.queryForObject("SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton=TRUE", UUID.class);
    }

    private <T> T tx(Supplier<T> action) {
        return transactions.execute(status -> action.get());
    }

    private static Jwt actor(String subject) {
        return new Jwt("test", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", subject));
    }
}
