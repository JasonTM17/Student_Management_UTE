package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    void specializedReleaseKeepsItsDomainAndHash() throws Exception {
        UUID release = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        String content = "Professional software design guidance";
        String hash = hash(source, "SPECIALIZED", "software-design", "Software design", content, "faculty", 10);
        Deque<HttpResponse<String>> responses = new ArrayDeque<>();
        responses.add(response(200, "[{\"id\":\"" + release + "\",\"corpus_version\":\"specialized-1\",\"corpus_hash\":\""
                + hash + "\",\"row_count\":1,\"status\":\"PUBLISHED\",\"manifest\":{}}]"));
        responses.add(response(200, "[{\"source_id\":\"" + source
                + "\",\"revision_id\":null,\"version\":1,\"domain\":\"SPECIALIZED\",\"slug\":\"software-design\",\"locale\":\"en\",\"title\":\"Software design\",\"content\":\""
                + content + "\",\"source\":\"faculty\",\"priority\":10,\"active\":true,\"visibility\":\"PUBLIC\",\"published_at\":\"2026-09-01T00:00:00Z\"}]"));

        SupabaseKnowledgeSyncService.SyncResult result = service(responses).syncNow();

        assertEquals("ACTIVATED", result.status(), result.message());
        assertEquals("SPECIALIZED", raw.queryForObject(
                "SELECT domain FROM assistant.knowledge_runtime_document WHERE release_id=?", String.class, release));
    }

    @Test
    void concurrentSyncsCannotActivateAnOlderSnapshotAfterANewerOne() throws Exception {
        UUID olderRelease = UUID.randomUUID();
        UUID newerRelease = UUID.randomUUID();
        UUID olderSource = UUID.randomUUID();
        UUID newerSource = UUID.randomUUID();
        String olderContent = "Older published guidance";
        String newerContent = "Newer published guidance";
        String olderHash = hash(olderSource, "POLICY", "release-order", "Release order", olderContent, "office", 10);
        String newerHash = hash(newerSource, "POLICY", "release-order", "Release order", newerContent, "office", 10);
        CountDownLatch olderDocumentsRequested = new CountDownLatch(1);
        CountDownLatch allowOlderDocuments = new CountDownLatch(1);
        CountDownLatch newerReleaseRequested = new CountDownLatch(1);

        HttpClient olderHttp = mock(HttpClient.class);
        when(olderHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    String uri = ((HttpRequest) invocation.getArgument(0)).uri().toString();
                    if (uri.contains("knowledge_release_document")) {
                        olderDocumentsRequested.countDown();
                        assertTrue(allowOlderDocuments.await(5, TimeUnit.SECONDS), "older document response was not released");
                        return response(200, "[{\"source_id\":\"" + olderSource + "\",\"revision_id\":null,\"version\":1,\"domain\":\"POLICY\",\"slug\":\"release-order\",\"locale\":\"en\",\"title\":\"Release order\",\"content\":\"" + olderContent + "\",\"source\":\"office\",\"priority\":10,\"active\":true,\"visibility\":\"PUBLIC\",\"published_at\":\"2026-09-01T00:00:00Z\"}]");
                    }
                    return response(200, "[{\"id\":\"" + olderRelease + "\",\"corpus_version\":\"older-release\",\"corpus_hash\":\"" + olderHash + "\",\"row_count\":1,\"status\":\"PUBLISHED\",\"manifest\":{}}]");
                });

        HttpClient newerHttp = mock(HttpClient.class);
        when(newerHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    String uri = ((HttpRequest) invocation.getArgument(0)).uri().toString();
                    if (uri.contains("knowledge_release_document")) {
                        return response(200, "[{\"source_id\":\"" + newerSource + "\",\"revision_id\":null,\"version\":1,\"domain\":\"POLICY\",\"slug\":\"release-order\",\"locale\":\"en\",\"title\":\"Release order\",\"content\":\"" + newerContent + "\",\"source\":\"office\",\"priority\":10,\"active\":true,\"visibility\":\"PUBLIC\",\"published_at\":\"2026-09-02T00:00:00Z\"}]");
                    }
                    newerReleaseRequested.countDown();
                    return response(200, "[{\"id\":\"" + newerRelease + "\",\"corpus_version\":\"newer-release\",\"corpus_hash\":\"" + newerHash + "\",\"row_count\":1,\"status\":\"PUBLISHED\",\"manifest\":{}}]");
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            SupabaseKnowledgeSyncService olderSync = service(olderHttp);
            SupabaseKnowledgeSyncService newerSync = service(newerHttp);
            Future<SupabaseKnowledgeSyncService.SyncResult> olderResult = executor.submit(olderSync::syncNow);
            assertTrue(olderDocumentsRequested.await(2, TimeUnit.SECONDS), "older snapshot did not reach its document fetch");
            CountDownLatch newerSyncStarted = new CountDownLatch(1);
            Future<SupabaseKnowledgeSyncService.SyncResult> newerResult = executor.submit(() -> {
                newerSyncStarted.countDown();
                return newerSync.syncNow();
            });
            assertTrue(newerSyncStarted.await(2, TimeUnit.SECONDS), "newer sync did not start");
            boolean newerFetchedBeforeOlderActivation = newerReleaseRequested.await(1, TimeUnit.SECONDS);

            allowOlderDocuments.countDown();
            assertEquals("ACTIVATED", olderResult.get(5, TimeUnit.SECONDS).status());
            assertEquals("ACTIVATED", newerResult.get(5, TimeUnit.SECONDS).status());
            assertEquals(newerRelease, raw.queryForObject(
                    "SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton=TRUE", UUID.class));
            assertFalse(newerFetchedBeforeOlderActivation,
                    "newer release fetch must wait for the older snapshot transaction to finish");
        } finally {
            allowOlderDocuments.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void invalidRuntimePointerFailsClosedBeforeAnyUpstreamRead() {
        HttpClient http = mock(HttpClient.class);

        raw.update("UPDATE assistant.knowledge_runtime_state SET active_release_id=NULL WHERE singleton=TRUE");
        assertRuntimePointerRejected(http);

        UUID danglingRelease = UUID.randomUUID();
        raw.update("UPDATE assistant.knowledge_runtime_state SET active_release_id=? WHERE singleton=TRUE", danglingRelease);
        assertRuntimePointerRejected(http);

        raw.update("UPDATE assistant.knowledge_release SET status='ARCHIVED' WHERE id=?", legacyRelease);
        raw.update("UPDATE assistant.knowledge_runtime_state SET active_release_id=? WHERE singleton=TRUE", legacyRelease);
        assertRuntimePointerRejected(http);

        try {
            verify(http, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        } catch (Exception impossible) {
            throw new AssertionError(impossible);
        }
    }

    @Test
    void missingRuntimeStateFailsClosedBeforeAnyUpstreamRead() throws Exception {
        raw.update("DELETE FROM assistant.knowledge_runtime_state WHERE singleton=TRUE");
        HttpClient http = mock(HttpClient.class);

        SupabaseKnowledgeSyncService.SyncResult result = service(http).syncNow();

        assertEquals("FAILED", result.status());
        assertEquals("Runtime projection state is unavailable", result.message());
        assertEquals(0, raw.queryForObject("SELECT COUNT(*) FROM assistant.knowledge_runtime_state", Integer.class));
        verify(http, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private void assertRuntimePointerRejected(HttpClient http) {
        SupabaseKnowledgeSyncService.SyncResult result = service(http).syncNow();

        assertEquals("FAILED", result.status());
        assertEquals("Runtime projection pointer is inconsistent", result.message());
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
        return service(http);
    }

    private SupabaseKnowledgeSyncService service(HttpClient http) {
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
