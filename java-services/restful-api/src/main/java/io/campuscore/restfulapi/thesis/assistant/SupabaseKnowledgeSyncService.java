package io.campuscore.restfulapi.thesis.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pulls a completely published Supabase release and promotes it as one
 * PostgreSQL runtime snapshot. No pointer update occurs until every document,
 * count, hash, and privacy check has succeeded in the same DB transaction.
 */
@Service
@Profile("persistence")
public class SupabaseKnowledgeSyncService {
    private final SupabaseKnowledgeProperties properties;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final TransactionTemplate transactions;

    private volatile SyncResult lastResult = SyncResult.disabled();

    @Autowired
    public SupabaseKnowledgeSyncService(SupabaseKnowledgeProperties properties,
            NamedParameterJdbcTemplate jdbc, ObjectMapper mapper,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this(properties, jdbc, mapper, transactionManager, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .build());
    }

    SupabaseKnowledgeSyncService(SupabaseKnowledgeProperties properties,
            NamedParameterJdbcTemplate jdbc, ObjectMapper mapper,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            HttpClient http) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.http = http;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** Operator-triggered sync; safe to call repeatedly for the same release. */
    public SyncResult syncNow() {
        if (!properties.usable()) {
            lastResult = SyncResult.disabled();
            return lastResult;
        }
        try {
            ReleasePayload release = fetchPublishedRelease();
            List<DocumentPayload> documents = fetchDocuments(release.id());
            validateRelease(release, documents);
            SyncResult result = transactions.execute(status -> activate(release, documents));
            lastResult = result == null ? SyncResult.failed("Activation returned no result") : result;
        } catch (SyncFailure failure) {
            // Deliberately omit upstream response bodies and credentials.
            lastResult = SyncResult.failed(failure.getMessage());
        } catch (DataAccessException failure) {
            lastResult = SyncResult.failed("Runtime projection database operation failed");
        } catch (RuntimeException failure) {
            lastResult = SyncResult.failed("Knowledge sync failed");
        }
        return lastResult;
    }

    @Scheduled(fixedDelayString = "${assistant.supabase.reconcile-delay-ms:900000}",
            initialDelayString = "${assistant.supabase.reconcile-initial-delay-ms:120000}")
    public void reconcile() {
        if (properties.enabled()) syncNow();
    }

    public SyncResult status() {
        try {
            List<SyncStatusRow> rows = jdbc.query(
                    "SELECT s.active_release_id, r.corpus_version, r.corpus_hash, r.row_count, r.status, r.activated_at "
                            + "FROM assistant.knowledge_runtime_state s LEFT JOIN assistant.knowledge_release r ON r.id=s.active_release_id "
                            + "WHERE s.singleton=TRUE",
                    Map.of(), (rs, row) -> new SyncStatusRow(
                            rs.getObject("active_release_id", UUID.class), rs.getString("corpus_version"),
                            rs.getString("corpus_hash"), rs.getObject("row_count", Integer.class),
                            rs.getString("status"), rs.getTimestamp("activated_at") == null ? null : rs.getTimestamp("activated_at").toInstant()));
            if (!rows.isEmpty()) {
                SyncStatusRow row = rows.get(0);
                return new SyncResult("ACTIVE", row.releaseId() == null ? null : row.releaseId().toString(),
                        row.corpusVersion(), row.corpusHash(), row.rowCount() == null ? 0 : row.rowCount(),
                        false, row.status());
            }
        } catch (DataAccessException ignored) {
            // Keep the last safe status; do not turn a status probe into a data leak.
        }
        return lastResult;
    }

    private SyncResult activate(ReleasePayload release, List<DocumentPayload> documents) {
        List<UUID> current = jdbc.query(
                "SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton=TRUE FOR UPDATE",
                Map.of(), (rs, row) -> rs.getObject("active_release_id", UUID.class));
        UUID currentId = current.isEmpty() ? null : current.get(0);
        if (release.id().equals(currentId)) {
            return new SyncResult("UNCHANGED", release.id().toString(), release.corpusVersion(), release.corpusHash(),
                    documents.size(), false, "Release is already active");
        }

        MapSqlParameterSource releaseParams = new MapSqlParameterSource()
                .addValue("id", release.id())
                .addValue("version", release.corpusVersion())
                .addValue("hash", release.corpusHash())
                .addValue("count", documents.size())
                .addValue("manifest", release.manifestJson())
                .addValue("previous", currentId)
                .addValue("actor", "supabase-sync");
        // The singleton row is locked above, so an explicit existence guard
        // keeps this statement portable to the H2 parity harness while still
        // making the PostgreSQL promotion deterministic.
        jdbc.update("INSERT INTO assistant.knowledge_release (id,corpus_version,corpus_hash,row_count,source,status,manifest,created_by,previous_release_id) "
                + "SELECT :id,:version,:hash,:count,'SUPABASE','STAGED',CAST(:manifest AS jsonb),:actor,:previous "
                + "WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id=:id)", releaseParams);
        List<ReleaseIdentity> existing = jdbc.query(
                "SELECT corpus_hash,status FROM assistant.knowledge_release WHERE id=:id FOR UPDATE",
                new MapSqlParameterSource("id", release.id()),
                (rs, row) -> new ReleaseIdentity(rs.getString("corpus_hash"), rs.getString("status")));
        if (existing.isEmpty() || !release.corpusHash().equals(existing.get(0).hash())) {
            throw new SyncFailure("Supabase release identity conflict");
        }
        boolean immutableReleaseExists = existing.get(0).status().equals("PUBLISHED");
        if (!immutableReleaseExists) {
            for (DocumentPayload document : documents) {
                jdbc.update("INSERT INTO assistant.knowledge_runtime_document "
                        + "(release_id,source_id,revision_id,version,domain,slug,locale,title,content,source,priority,active,visibility,published_at) "
                        + "VALUES (:release,:source,:revision,:version,:domain,:slug,:locale,:title,:content,:origin,:priority,:active,'PUBLIC',:published)",
                        new MapSqlParameterSource()
                                .addValue("release", release.id()).addValue("source", document.sourceId())
                                .addValue("revision", document.revisionId()).addValue("version", document.version())
                                .addValue("domain", document.domain()).addValue("slug", document.slug())
                                .addValue("locale", document.locale()).addValue("title", document.title())
                                .addValue("content", document.content()).addValue("origin", document.source())
                                .addValue("priority", document.priority()).addValue("active", document.active())
                                .addValue("published", Timestamp.from(document.publishedAt())));
            }
        }
        Integer stagedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant.knowledge_runtime_document WHERE release_id=:release",
                new MapSqlParameterSource("release", release.id()), Integer.class);
        if (stagedCount == null || stagedCount != documents.size()) throw new SyncFailure("Runtime projection count mismatch");
        if (!immutableReleaseExists) {
            jdbc.update("UPDATE assistant.knowledge_release SET status='PUBLISHED', activated_at=CURRENT_TIMESTAMP, previous_release_id=:previous WHERE id=:id",
                    new MapSqlParameterSource("id", release.id()).addValue("previous", currentId));
        }
        int switched = jdbc.update("UPDATE assistant.knowledge_runtime_state SET active_release_id=:id,updated_at=CURRENT_TIMESTAMP WHERE singleton=TRUE",
                new MapSqlParameterSource("id", release.id()));
        if (switched == 0) {
            jdbc.update("INSERT INTO assistant.knowledge_runtime_state(singleton,active_release_id) VALUES(TRUE,:id)",
                    new MapSqlParameterSource("id", release.id()));
        }
        return new SyncResult("ACTIVATED", release.id().toString(), release.corpusVersion(), release.corpusHash(),
                documents.size(), false, "Release activated atomically");
    }

    private ReleasePayload fetchPublishedRelease() {
        String endpoint = restEndpoint(properties.releaseTable())
                + "?select=id,corpus_version,corpus_hash,row_count,status,manifest&status=eq.PUBLISHED&order=published_at.desc&limit=1";
        JsonNode array = request(endpoint);
        if (!array.isArray() || array.isEmpty()) throw new SyncFailure("No published Supabase release is available");
        JsonNode row = array.get(0);
        UUID id = uuid(row, "id");
        String version = required(row, "corpus_version");
        String hash = required(row, "corpus_hash").toLowerCase(java.util.Locale.ROOT);
        int count = integer(row, "row_count");
        if (!hash.matches("[0-9a-f]{64}") || count < 0) throw new SyncFailure("Supabase release manifest is invalid");
        return new ReleasePayload(id, version, hash, count, row.path("manifest").isObject() ? row.path("manifest").toString() : "{}");
    }

    private List<DocumentPayload> fetchDocuments(UUID releaseId) {
        String endpoint = restEndpoint(properties.documentTable())
                + "?select=source_id,revision_id,version,domain,slug,locale,title,content,source,priority,active,visibility,published_at"
                + "&release_id=eq." + url(releaseId.toString()) + "&order=source_id.asc&limit=10000";
        JsonNode array = request(endpoint);
        if (!array.isArray()) throw new SyncFailure("Supabase release documents response is invalid");
        List<DocumentPayload> documents = new ArrayList<>();
        for (JsonNode row : array) {
            documents.add(new DocumentPayload(
                    required(row, "source_id"), optionalUuid(row, "revision_id"), integerOr(row, "version", 0),
                    AssistantKnowledgeDomains.normalize(required(row, "domain")), required(row, "slug"),
                    required(row, "locale"), required(row, "title"), required(row, "content"),
                    required(row, "source"), integerOr(row, "priority", 100), row.path("active").asBoolean(false),
                    required(row, "visibility").toUpperCase(java.util.Locale.ROOT), instant(row, "published_at")));
        }
        return documents;
    }

    private void validateRelease(ReleasePayload release, List<DocumentPayload> documents) {
        if (release.rowCount() != documents.size()) throw new SyncFailure("Supabase release row count mismatch");
        Set<String> ids = new HashSet<>();
        Set<String> slugs = new HashSet<>();
        for (DocumentPayload document : documents) {
            if (!ids.add(document.sourceId()) || !slugs.add(document.slug())) throw new SyncFailure("Supabase release contains duplicate identity");
            if (!AssistantKnowledgeDomains.isAllowed(document.domain()) || !Set.of("vi", "en", "both").contains(document.locale())
                    || !"PUBLIC".equals(document.visibility())) throw new SyncFailure("Supabase release contains a non-public document");
            if (document.slug().length() > 180 || document.title().length() > 500 || document.content().length() > 50_000
                    || document.source().length() > 240 || document.priority() < 1 || document.priority() > 1000) {
                throw new SyncFailure("Supabase release document exceeds limits");
            }
            for (String value : List.of(document.slug(), document.title(), document.content(), document.source())) {
                if (!AssistantInputGuard.inspectPublicKnowledge(value).allowed()) throw new SyncFailure("Supabase release privacy validation failed");
            }
        }
        String computed = sha256(documents.stream().sorted(Comparator.comparing(DocumentPayload::sourceId))
                .map(document -> String.join("|", document.sourceId(), safe(document.revisionId()), Integer.toString(document.version()),
                        document.domain(), document.slug(), document.locale(), document.title(), document.content(), document.source(),
                        Integer.toString(document.priority()), Boolean.toString(document.active()), document.visibility()))
                .collect(Collectors.joining("\n")));
        if (!computed.equals(release.corpusHash())) throw new SyncFailure("Supabase release SHA-256 does not match manifest");
    }

    private JsonNode request(String endpoint) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMillis(properties.readTimeoutMs()))
                .header("apikey", properties.serviceRoleKey())
                .header("Authorization", "Bearer " + properties.serviceRoleKey())
                .header("Accept-Profile", properties.schema())
                .header("Accept", "application/json")
                .GET().build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) throw new SyncFailure("Supabase request was rejected");
            try { return mapper.readTree(response.body()); }
            catch (IOException malformed) { throw new SyncFailure("Supabase response was malformed"); }
        } catch (IOException exception) {
            throw new SyncFailure("Supabase request failed");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SyncFailure("Supabase request was interrupted");
        }
    }

    private String restEndpoint(String table) {
        return properties.url().replaceAll("/$", "") + "/rest/v1/" + url(table);
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).isValueNode() ? node.path(field).asText() : "";
        if (value == null || value.isBlank()) throw new SyncFailure("Supabase release field is missing");
        return value.trim();
    }
    private static int integer(JsonNode node, String field) { if (!node.path(field).canConvertToInt()) throw new SyncFailure("Supabase release count is invalid"); return node.path(field).asInt(); }
    private static int integerOr(JsonNode node, String field, int fallback) { return node.path(field).canConvertToInt() ? node.path(field).asInt() : fallback; }
    private static UUID uuid(JsonNode node, String field) { try { return UUID.fromString(required(node, field)); } catch (IllegalArgumentException invalid) { throw new SyncFailure("Supabase release id is invalid"); } }
    private static UUID optionalUuid(JsonNode node, String field) { String value = node.path(field).asText(""); if (value.isBlank()) return null; try { return UUID.fromString(value); } catch (IllegalArgumentException invalid) { throw new SyncFailure("Supabase revision id is invalid"); } }
    private static Instant instant(JsonNode node, String field) { try { return Instant.parse(required(node, field)); } catch (RuntimeException invalid) { throw new SyncFailure("Supabase release timestamp is invalid"); } }
    private static String safe(UUID value) { return value == null ? "" : value.toString(); }
    private static String url(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception impossible) { throw new IllegalStateException(impossible); } }

    public record SyncResult(String status, String releaseId, String corpusVersion, String corpusHash, int rowCount,
            boolean degraded, String message) {
        static SyncResult disabled() { return new SyncResult("DISABLED", null, null, null, 0, true, "Supabase authoring sync is not configured"); }
        static SyncResult failed(String message) { return new SyncResult("FAILED", null, null, null, 0, true, message == null ? "Knowledge sync failed" : message); }
    }
    private record ReleasePayload(UUID id, String corpusVersion, String corpusHash, int rowCount, String manifestJson) { }
    private record DocumentPayload(String sourceId, UUID revisionId, int version, String domain, String slug, String locale,
            String title, String content, String source, int priority, boolean active, String visibility, Instant publishedAt) { }
    private record ReleaseIdentity(String hash, String status) { }
    private record SyncStatusRow(UUID releaseId, String corpusVersion, String corpusHash, Integer rowCount, String status, Instant activatedAt) { }
    private static final class SyncFailure extends RuntimeException { private static final long serialVersionUID = 1L; SyncFailure(String message) { super(message); } }
}
