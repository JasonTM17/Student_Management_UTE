package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.campuscore.restfulapi.thesis.assistant.AssistantRlsBoundary.Access;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Real-PostgreSQL regression for the scheduled prerequisite-map sync. The job
 * writes governed knowledge without an authenticated user, so it must run
 * inside the CATALOG_SYNC RLS boundary — verified here against real policies.
 * Requires a disposable database (the run regenerates the two map documents).
 */
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
class PrerequisiteMapSyncPostgresIT {

    @Autowired PrerequisiteMapSyncJob job;
    @Autowired AssistantRlsTransactionRunner transactions;
    @Autowired @Qualifier("namedParameterJdbcTemplate") NamedParameterJdbcTemplate primaryJdbc;
    @Autowired @Qualifier(AssistantDatabaseConfiguration.JDBC_TEMPLATE) NamedParameterJdbcTemplate assistantJdbc;

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

    @Test
    void driftedMapDocumentsAreRegeneratedAndPromotedUnderCatalogSync() {
        String slug = "catalog-prerequisite-map-vi";
        UUID documentId = primaryJdbc.queryForObject(
                "SELECT id FROM assistant.knowledge_document WHERE slug=:slug AND active",
                new MapSqlParameterSource("slug", slug), UUID.class);
        assertNotNull(documentId);

        Integer beforeVersion = primaryJdbc.queryForObject(
                "SELECT MAX(version) FROM assistant.knowledge_document_revision WHERE document_id=:id",
                new MapSqlParameterSource("id", documentId), Integer.class);
        UUID previousRelease = primaryJdbc.queryForObject(
                "SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton=TRUE",
                Map.of(), UUID.class);

        // The promoter refuses while a foreign authority owns the active
        // release; production runs MANUAL, so mirror that authority here.
        primaryJdbc.update(
                "UPDATE assistant.knowledge_release SET source='MANUAL' WHERE id=:id",
                new MapSqlParameterSource("id", previousRelease));

        // Force authoring drift so the sync must regenerate the document.
        primaryJdbc.update("UPDATE assistant.knowledge_document SET content='stale' WHERE id=:id",
                new MapSqlParameterSource("id", documentId));

        job.synchronizeMapDocuments();

        Map<String, Object> revision = primaryJdbc.queryForMap(
                "SELECT version,state,created_by,reviewed_by FROM assistant.knowledge_document_revision "
                        + "WHERE document_id=:id ORDER BY version DESC LIMIT 1",
                new MapSqlParameterSource("id", documentId));
        assertEquals(beforeVersion + 1, ((Number) revision.get("version")).intValue());
        assertEquals("PUBLISHED", revision.get("state"));
        assertEquals("prerequisite-map-sync", revision.get("created_by"));
        assertEquals("prerequisite-map-sync", revision.get("reviewed_by"));

        Long published = primaryJdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant.knowledge_document_revision "
                        + "WHERE document_id=:id AND state='PUBLISHED'",
                new MapSqlParameterSource("id", documentId), Long.class);
        assertEquals(1L, published, "exactly one published revision after regeneration");

        String content = primaryJdbc.queryForObject(
                "SELECT content FROM assistant.knowledge_document WHERE id=:id",
                new MapSqlParameterSource("id", documentId), String.class);
        assertNotNull(content);
        assertTrue(content.contains("Học phần tiên quyết"), "document content refreshed from live rows");

        UUID activeRelease = primaryJdbc.queryForObject(
                "SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton=TRUE",
                Map.of(), UUID.class);
        assertNotNull(activeRelease);
        if (!activeRelease.equals(previousRelease)) {
            String actor = primaryJdbc.queryForObject(
                    "SELECT created_by FROM assistant.knowledge_release WHERE id=:id",
                    new MapSqlParameterSource("id", activeRelease), String.class);
            assertEquals("prerequisite-map-sync", actor);
        }
    }

    @Test
    void secondRunIsIdempotentAndScopeCannotSeeOtherDocuments() {
        Integer releasesBefore = primaryJdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant.knowledge_release",
                Map.of(), Integer.class);

        job.synchronizeMapDocuments();

        Integer releasesAfter = primaryJdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant.knowledge_release",
                Map.of(), Integer.class);
        assertEquals(releasesBefore, releasesAfter,
                "a converged corpus must not mint a new release");

        // CATALOG_SYNC must stay narrowed to the map document source.
        List<String> otherSources = transactions.executeUnchecked(Access.CATALOG_SYNC,
                () -> assistantJdbc.query(
                        "SELECT DISTINCT source FROM assistant.knowledge_document "
                                + "WHERE source <> 'campuscore-prerequisite-map'",
                        Map.of(), (rs, i) -> rs.getString(1)));
        assertTrue(otherSources.isEmpty(), "catalog sync scope must not read foreign documents");
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable " + name);
        }
        return value;
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
