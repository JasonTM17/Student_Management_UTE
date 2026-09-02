package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * Deterministic sync oracle: fetch/validate/stage/pointer failures must never
 * replace the last published runtime release.
 */
class SupabaseKnowledgeSyncServiceTest {
    private JdbcTemplate raw;
    private NamedParameterJdbcTemplate jdbc;
    private UUID legacyRelease;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:assistant_sync_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        raw = new JdbcTemplate(dataSource);
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        raw.execute("CREATE SCHEMA assistant");
        raw.execute("CREATE TABLE assistant.knowledge_release (id UUID PRIMARY KEY, corpus_version VARCHAR(120) NOT NULL, corpus_hash CHAR(64) NOT NULL UNIQUE, row_count INTEGER NOT NULL, source VARCHAR(24) NOT NULL, status VARCHAR(24) NOT NULL, manifest JSON, created_by VARCHAR(120) NOT NULL, activated_at TIMESTAMP WITH TIME ZONE, previous_release_id UUID)");
        raw.execute("CREATE TABLE assistant.knowledge_runtime_document (release_id UUID NOT NULL, source_id VARCHAR(180) NOT NULL, revision_id UUID, version INTEGER NOT NULL, domain VARCHAR(48) NOT NULL, slug VARCHAR(180) NOT NULL, locale VARCHAR(8) NOT NULL, title VARCHAR(500) NOT NULL, content VARCHAR(50) NOT NULL, source VARCHAR(240) NOT NULL, priority SMALLINT NOT NULL, active BOOLEAN NOT NULL, visibility VARCHAR(32) NOT NULL, published_at TIMESTAMP WITH TIME ZONE NOT NULL, PRIMARY KEY (release_id, source_id))");
        raw.execute("CREATE TABLE assistant.knowledge_runtime_state (singleton BOOLEAN PRIMARY KEY, active_release_id UUID, updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)");
        legacyRelease = UUID.fromString("00000000-0000-0000-0000-000000000016");
        raw.update("INSERT INTO assistant.knowledge_release(id,corpus_version,corpus_hash,row_count,source,status,manifest,created_by) VALUES (?,?,?,?,?,?,JSON '{}',?)",
                legacyRelease, "legacy", "a".repeat(64), 0, "LEGACY", "PUBLISHED", "system");
        raw.update("INSERT INTO assistant.knowledge_runtime_state(singleton,active_release_id) VALUES(TRUE,?)", legacyRelease);
    }

    @Test
    void fullyValidatedReleaseSwitchesPointerAndExposesReleaseProvenance() throws Exception {
        UUID release = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        String content = "Registration opens Monday";
        String hash = hash(source, "REGISTRATION", "registration-window", "Registration window", content, "registrar", 10);
        Deque<HttpResponse<String>> responses = new ArrayDeque<>();
        responses.add(response(200, "[{\"id\":\"" + release + "\",\"corpus_version\":\"campus-1\",\"corpus_hash\":\"" + hash + "\",\"row_count\":1,\"status\":\"PUBLISHED\",\"manifest\":{\"schemaVersion\":1}}]"));
        responses.add(response(200, "[{\"source_id\":\"" + source + "\",\"revision_id\":null,\"version\":1,\"domain\":\"REGISTRATION\",\"slug\":\"registration-window\",\"locale\":\"en\",\"title\":\"Registration window\",\"content\":\"" + content + "\",\"source\":\"registrar\",\"priority\":10,\"active\":true,\"visibility\":\"PUBLIC\",\"published_at\":\"2026-09-01T00:00:00Z\"}]"));

        SupabaseKnowledgeSyncService.SyncResult result = service(responses).syncNow();

        assertEquals("ACTIVATED", result.status(), result.message());
        assertEquals(release.toString(), result.releaseId());
        assertEquals("campus-1", result.corpusVersion());
        assertEquals(hash, result.corpusHash());
        assertEquals(release, raw.queryForObject("SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton=TRUE", UUID.class));
        assertEquals(1, raw.queryForObject("SELECT COUNT(*) FROM assistant.knowledge_runtime_document WHERE release_id=?", Integer.class, release));
    }

    @Test
    void validationFailureRetainsLastPublishedRelease() {
        UUID release = UUID.randomUUID();
        Deque<HttpResponse<String>> responses = new ArrayDeque<>();
        responses.add(response(200, "[{\"id\":\"" + release + "\",\"corpus_version\":\"campus-bad\",\"corpus_hash\":\"" + "b".repeat(64) + "\",\"row_count\":2,\"status\":\"PUBLISHED\",\"manifest\":{}}]"));
        responses.add(response(200, "[]"));

        SupabaseKnowledgeSyncService.SyncResult result = service(responses).syncNow();

        assertEquals("FAILED", result.status());
        assertTrue(result.degraded());
        assertEquals(legacyRelease, raw.queryForObject("SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton=TRUE", UUID.class));
        assertEquals(0, raw.queryForObject("SELECT COUNT(*) FROM assistant.knowledge_runtime_document WHERE release_id=?", Integer.class, release));
    }

    @Test
    void upstreamFetchFailureRetainsLastPublishedRelease() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("simulated upstream outage"));
        SupabaseKnowledgeProperties properties = new SupabaseKnowledgeProperties(true,
                "https://supabase.example", "service-role", "assistant",
                "knowledge_release", "knowledge_release_document", 500, 1_000);
        SupabaseKnowledgeSyncService sync = new SupabaseKnowledgeSyncService(properties, jdbc,
                new ObjectMapper(), new DataSourceTransactionManager(jdbc.getJdbcTemplate().getDataSource()), http);

        SupabaseKnowledgeSyncService.SyncResult result = sync.syncNow();

        assertEquals("FAILED", result.status());
        assertEquals(legacyRelease, raw.queryForObject("SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton=TRUE", UUID.class));
    }

    @Test
    void stageConstraintFailureRollsBackReleaseRowsAndPointer() throws Exception {
        UUID release = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        String content = "This content is intentionally longer than the test projection limit";
        String hash = hash(source, "POLICY", "policy", "Policy", content, "office", 10);
        Deque<HttpResponse<String>> responses = new ArrayDeque<>();
        responses.add(response(200, "[{\"id\":\"" + release + "\",\"corpus_version\":\"campus-stage-failure\",\"corpus_hash\":\"" + hash + "\",\"row_count\":1,\"status\":\"PUBLISHED\",\"manifest\":{}}]"));
        responses.add(response(200, "[{\"source_id\":\"" + source + "\",\"version\":1,\"domain\":\"POLICY\",\"slug\":\"policy\",\"locale\":\"en\",\"title\":\"Policy\",\"content\":\"" + content + "\",\"source\":\"office\",\"priority\":10,\"active\":true,\"visibility\":\"PUBLIC\",\"published_at\":\"2026-09-01T00:00:00Z\"}]"));

        SupabaseKnowledgeSyncService.SyncResult result = service(responses).syncNow();

        assertEquals("FAILED", result.status());
        assertEquals(legacyRelease, raw.queryForObject("SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton=TRUE", UUID.class));
        assertEquals(0, raw.queryForObject("SELECT COUNT(*) FROM assistant.knowledge_release WHERE id=?", Integer.class, release));
        assertEquals(0, raw.queryForObject("SELECT COUNT(*) FROM assistant.knowledge_runtime_document WHERE release_id=?", Integer.class, release));
    }

    @Test
    void pointerSwitchFailureRollsBackStagedRelease() throws Exception {
        raw.execute("ALTER TABLE assistant.knowledge_runtime_state ADD CONSTRAINT only_legacy_pointer CHECK (active_release_id = '00000000-0000-0000-0000-000000000016')");
        UUID release = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        String content = "Public policy";
        String hash = hash(source, "POLICY", "policy-pointer", "Policy", content, "office", 10);
        Deque<HttpResponse<String>> responses = new ArrayDeque<>();
        responses.add(response(200, "[{\"id\":\"" + release + "\",\"corpus_version\":\"campus-pointer-failure\",\"corpus_hash\":\"" + hash + "\",\"row_count\":1,\"status\":\"PUBLISHED\",\"manifest\":{}}]"));
        responses.add(response(200, "[{\"source_id\":\"" + source + "\",\"version\":1,\"domain\":\"POLICY\",\"slug\":\"policy-pointer\",\"locale\":\"en\",\"title\":\"Policy\",\"content\":\"" + content + "\",\"source\":\"office\",\"priority\":10,\"active\":true,\"visibility\":\"PUBLIC\",\"published_at\":\"2026-09-01T00:00:00Z\"}]"));

        SupabaseKnowledgeSyncService.SyncResult result = service(responses).syncNow();

        assertEquals("FAILED", result.status());
        assertEquals(legacyRelease, raw.queryForObject("SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton=TRUE", UUID.class));
        assertEquals(0, raw.queryForObject("SELECT COUNT(*) FROM assistant.knowledge_release WHERE id=?", Integer.class, release));
    }

    private SupabaseKnowledgeSyncService service(Deque<HttpResponse<String>> responses) {
        HttpClient http = mock(HttpClient.class);
        try {
            when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> responses.removeFirst());
        } catch (Exception impossible) {
            throw new AssertionError(impossible);
        }
        SupabaseKnowledgeProperties properties = new SupabaseKnowledgeProperties(true,
                "https://supabase.example", "service-role", "assistant",
                "knowledge_release", "knowledge_release_document", 500, 1_000);
        return new SupabaseKnowledgeSyncService(properties, jdbc, new ObjectMapper(),
                new DataSourceTransactionManager(jdbc.getJdbcTemplate().getDataSource()), http);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static String hash(UUID source, String domain, String slug, String title, String content,
            String sourceName, int priority) throws Exception {
        String material = String.join("|", source.toString(), "", "1", domain, slug, "en", title, content,
                sourceName, Integer.toString(priority), "true", "PUBLIC");
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(material.getBytes(StandardCharsets.UTF_8)));
    }
}
