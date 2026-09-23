package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.web.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Promotes a governed SQL publication into the immutable retrieval snapshot. */
@Service
@Profile("persistence")
public class SqlKnowledgeReleasePromoter {
    private final NamedParameterJdbcTemplate jdbc;
    private final boolean legacyTestFallback;

    public SqlKnowledgeReleasePromoter(NamedParameterJdbcTemplate jdbc,
            @Value("${assistant.legacy-retrieval-fallback:false}") boolean legacyTestFallback) {
        this.jdbc = jdbc;
        this.legacyTestFallback = legacyTestFallback;
    }

    /** Must run inside the caller's authoring transaction; any failure rolls it back. */
    public void promote(UUID documentId, UUID publishedRevisionId, String actor) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Knowledge promotion requires the authoring transaction");
        }
        List<ActiveRelease> current;
        try {
            // Every promoter and the Supabase sync serialize on this one row.
            current = jdbc.query("SELECT s.active_release_id,r.source,r.status "
                            + "FROM assistant.knowledge_runtime_state s "
                            + "JOIN assistant.knowledge_release r ON r.id=s.active_release_id "
                            + "WHERE s.singleton=TRUE FOR UPDATE",
                    Map.of(), (rs, index) -> new ActiveRelease(
                            rs.getObject("active_release_id", UUID.class),
                            rs.getString("source"), rs.getString("status")));
        } catch (BadSqlGrammarException missingProjection) {
            // Only the explicit pre-V16 H2 test profile may exercise authoring
            // without runtime tables. Production has this flag disabled.
            if (legacyTestFallback) return;
            throw missingProjection;
        }
        if (current.size() != 1 || !"PUBLISHED".equals(current.get(0).status())) {
            throw conflict("No published runtime release is active");
        }
        ActiveRelease previous = current.get(0);
        if (!"MANUAL".equals(previous.source()) && !"LEGACY".equals(previous.source())) {
            throw conflict("The active knowledge release is owned by another authority");
        }

        List<RuntimeRow> before = readRows(previous.id());
        List<RuntimeRow> after = new ArrayList<>(before);
        after.removeIf(row -> row.sourceId().equals(documentId.toString()));
        if (publishedRevisionId != null) after.add(readPublished(documentId, publishedRevisionId));
        after.sort(Comparator.comparing(RuntimeRow::sourceId));
        if (before.equals(after)) return;

        String hash = canonicalHash(after);
        List<UUID> matching = jdbc.query(
                "SELECT id FROM assistant.knowledge_release WHERE corpus_hash=:hash AND status='PUBLISHED'",
                new MapSqlParameterSource("hash", hash),
                (rs, index) -> rs.getObject("id", UUID.class));
        UUID releaseId;
        if (!matching.isEmpty()) {
            releaseId = matching.get(0);
            // A historical hash may come from an older canonicalization. Never
            // activate it merely because the digest happened to match.
            if (!after.equals(readRows(releaseId))) throw conflict("Existing release rows do not match the new corpus");
        } else {
            releaseId = UUID.randomUUID();
            String version = "manual-" + releaseId;
            String manifest = "{\"schemaVersion\":1,\"corpusVersion\":\"" + version
                    + "\",\"rowCount\":" + after.size() + ",\"sha256\":\"" + hash + "\"}";
            jdbc.update("INSERT INTO assistant.knowledge_release "
                            + "(id,corpus_version,corpus_hash,row_count,source,status,manifest,created_by,activated_at,previous_release_id) "
                            + "VALUES (:id,:version,:hash,:count,'MANUAL','PUBLISHED',CAST(:manifest AS jsonb),:actor,CURRENT_TIMESTAMP,:previous)",
                    new MapSqlParameterSource().addValue("id", releaseId).addValue("version", version)
                            .addValue("hash", hash).addValue("count", after.size()).addValue("manifest", manifest)
                            .addValue("actor", actor).addValue("previous", previous.id()));
            for (RuntimeRow row : after) {
                jdbc.update("INSERT INTO assistant.knowledge_runtime_document "
                                + "(release_id,source_id,revision_id,version,domain,slug,locale,title,content,source,priority,active,visibility,published_at) "
                                + "VALUES (:release,:sourceId,:revision,:version,:domain,:slug,:locale,:title,:content,:source,:priority,:active,:visibility,:published)",
                        new MapSqlParameterSource().addValue("release", releaseId)
                                .addValue("sourceId", row.sourceId()).addValue("revision", row.revisionId())
                                .addValue("version", row.version()).addValue("domain", row.domain())
                                .addValue("slug", row.slug()).addValue("locale", row.locale())
                                .addValue("title", row.title()).addValue("content", row.content())
                                .addValue("source", row.source()).addValue("priority", row.priority())
                                .addValue("active", row.active()).addValue("visibility", row.visibility())
                                .addValue("published", Timestamp.from(row.publishedAt())));
            }
            if (!after.equals(readRows(releaseId))) throw conflict("Runtime release projection differs from authoring");
        }
        int switched = jdbc.update("UPDATE assistant.knowledge_runtime_state "
                        + "SET active_release_id=:release,updated_at=CURRENT_TIMESTAMP "
                        + "WHERE singleton=TRUE AND active_release_id=:previous",
                new MapSqlParameterSource().addValue("release", releaseId).addValue("previous", previous.id()));
        if (switched != 1) throw conflict("Runtime release changed during publication");
    }

    private RuntimeRow readPublished(UUID documentId, UUID revisionId) {
        List<RuntimeRow> rows = jdbc.query("SELECT r.id AS revision_id,r.version,r.domain,r.slug,r.locale,r.title,r.content,r.source,r.priority,"
                        + "r.published_at FROM assistant.knowledge_document_revision r "
                        + "JOIN assistant.knowledge_document d ON d.id=r.document_id "
                        + "WHERE d.id=:document AND d.active=TRUE AND d.visibility='PUBLIC' "
                        + "AND r.id=:revision AND r.state='PUBLISHED'",
                new MapSqlParameterSource().addValue("document", documentId).addValue("revision", revisionId),
                (rs, index) -> new RuntimeRow(documentId.toString(), rs.getObject("revision_id", UUID.class),
                        rs.getInt("version"), rs.getString("domain"), rs.getString("slug"),
                        rs.getString("locale"), rs.getString("title"), rs.getString("content"),
                        rs.getString("source"), rs.getInt("priority"), true, "PUBLIC",
                        rs.getTimestamp("published_at").toInstant()));
        if (rows.size() != 1) throw conflict("Published authoring revision is unavailable");
        return rows.get(0);
    }

    private List<RuntimeRow> readRows(UUID releaseId) {
        return jdbc.query("SELECT source_id,revision_id,version,domain,slug,locale,title,content,source,priority,active,visibility,published_at "
                        + "FROM assistant.knowledge_runtime_document WHERE release_id=:release ORDER BY source_id",
                new MapSqlParameterSource("release", releaseId), SqlKnowledgeReleasePromoter::mapRow);
    }

    private static RuntimeRow mapRow(ResultSet rs, int index) throws SQLException {
        return new RuntimeRow(rs.getString("source_id"), rs.getObject("revision_id", UUID.class),
                rs.getInt("version"), rs.getString("domain"), rs.getString("slug"),
                rs.getString("locale"), rs.getString("title"), rs.getString("content"),
                rs.getString("source"), rs.getInt("priority"), rs.getBoolean("active"),
                rs.getString("visibility"), rs.getTimestamp("published_at").toInstant());
    }

    private static String canonicalHash(List<RuntimeRow> rows) {
        String canonical = rows.stream().sorted(Comparator.comparing(RuntimeRow::sourceId))
                .map(row -> String.join("|", row.sourceId(), row.revisionId() == null ? "" : row.revisionId().toString(),
                        Integer.toString(row.version()), row.domain(), row.slug(), row.locale(), row.title(),
                        row.content(), row.source(), Integer.toString(row.priority()), Boolean.toString(row.active()),
                        row.visibility()))
                .collect(Collectors.joining("\n"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static DomainException conflict(String message) {
        return new DomainException(HttpStatus.CONFLICT, "KNOWLEDGE_RELEASE_CONFLICT", message);
    }

    private record ActiveRelease(UUID id, String source, String status) { }

    private record RuntimeRow(String sourceId, UUID revisionId, int version, String domain, String slug,
            String locale, String title, String content, String source, int priority, boolean active,
            String visibility, Instant publishedAt) { }
}
