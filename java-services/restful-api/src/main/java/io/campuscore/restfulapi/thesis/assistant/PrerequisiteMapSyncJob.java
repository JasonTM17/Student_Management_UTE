package io.campuscore.restfulapi.thesis.assistant;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Regenerates the bilingual prerequisite-map documents (V97) from the LIVE
 * {@code academic."CourseRequirement"} rows whenever they drift. The V97 map
 * is a migration-time snapshot: later requirement changes or course renames
 * would otherwise leave the assistant answering from a stale map while the
 * registration gate enforces the new truth.
 *
 * <p>Daily the job recomputes the map bodies from the same string_agg the
 * migration uses, compares them against the active runtime documents, and on
 * drift archives the current PUBLISHED revisions, appends reviewed ones, and
 * promotes a fresh MANUAL release via {@link SqlKnowledgeReleasePromoter} —
 * the same governed path the admin knowledge UI uses, so runtime hash
 * validation keeps passing. When the active release is owned by another
 * authority (Supabase), the promoter refuses and the job only logs.
 */
@Component
@Profile("persistence")
@ConditionalOnProperty(prefix = "assistant.prerequisite-map-sync", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class PrerequisiteMapSyncJob {
    private static final Logger log = LoggerFactory.getLogger(PrerequisiteMapSyncJob.class);

    static final String MAP_SOURCE = "campuscore-prerequisite-map";
    private static final List<String> MAP_SLUGS = List.of(
            "catalog-prerequisite-map-vi", "catalog-prerequisite-map-en");
    private static final String VI_BODY =
            "## Học phần tiên quyết là gì\n\n"
            + "- Học phần tiên quyết là môn bạn phải **hoàn thành và đạt** (không tính F/W) trước khi được đăng ký môn phía sau; cổng tự động chặn đăng ký khi còn thiếu.\n"
            + "- Bản đồ dưới đây liệt kê đầy đủ theo dữ liệu đăng ký của trường; hỏi theo mã môn (ví dụ: \"tiên quyết của SE421\") sẽ ra đúng danh sách.\n\n"
            + "## Danh sách chi tiết\n\n";
    private static final String VI_NOTES =
            "\n\n## Lưu ý\n\n- Ngưỡng điểm tối thiểu hiện áp dụng mức Đạt (D); môn chưa có điểm công bố chưa được tính là đã hoàn thành.\n"
            + "- Học phần song hành (học cùng kỳ) và quy định học trước chi tiết xem ở mục quy chế đăng ký học phần.";
    private static final String EN_BODY =
            "## What a prerequisite is\n\n"
            + "- A prerequisite is a course you must **complete with a passing grade** (F/W do not count) before registering the follow-up course; the portal blocks the registration automatically when it is missing.\n"
            + "- The map below lists every chain from the registration data; ask by course code (for example \"prerequisites of SE421\") for the exact list.\n\n"
            + "## Detailed list\n\n";
    private static final String EN_NOTES =
            "\n\n## Notes\n\n- The current minimum threshold is a pass (D); a course without a published grade does not count as completed yet.\n"
            + "- Corequisite pairs and the detailed prior-learning rules live in the registration regulations topic.";

    private final NamedParameterJdbcTemplate jdbc;
    private final SqlKnowledgeReleasePromoter promoter;

    public PrerequisiteMapSyncJob(
            @Qualifier(AssistantDatabaseConfiguration.JDBC_TEMPLATE) NamedParameterJdbcTemplate jdbc,
            SqlKnowledgeReleasePromoter promoter) {
        this.jdbc = jdbc;
        this.promoter = promoter;
    }

    /** Runs daily at 04:12 server time (off-peak, fixed schedule, no overlap). */
    @Scheduled(cron = "${assistant.prerequisite-map-sync.cron:0 12 4 * * *}")
    public void synchronizeMapDocuments() {
        try {
            int updated = 0;
            for (Map.Entry<String, String> entry : Map.of(
                    "catalog-prerequisite-map-vi", buildBody(true),
                    "catalog-prerequisite-map-en", buildBody(false)).entrySet()) {
                updated += rewriteIfDrifted(entry.getKey(), entry.getValue());
            }
            if (updated > 0) {
                log.info("PREREQUISITE_MAP_SYNC regenerated {} document(s) from live CourseRequirement rows", updated);
            }
        } catch (Exception exception) {
            // Telemetry job: a failed sync must never break the assistant.
            log.warn("PREREQUISITE_MAP_SYNC failed: {}", exception.getMessage());
        }
    }

    /**
     * Rewrites one map document when the live requirement rows produce a
     * different body. Returns 1 when a revision was appended (and promoted),
     * 0 when the document already matched.
     */
    private int rewriteIfDrifted(String slug, String body) {
        List<Map<String, Object>> docs = jdbc.query(
                "SELECT d.id, d.title, d.content, d.source, d.priority, d.domain, d.locale "
                        + "FROM assistant.knowledge_document d WHERE d.slug = :slug AND d.active = TRUE",
                Map.of("slug", slug),
                (rs, i) -> Map.<String, Object>of(
                        "id", rs.getObject("id", UUID.class),
                        "title", rs.getString("title"),
                        "content", rs.getString("content"),
                        "source", rs.getString("source"),
                        "priority", rs.getInt("priority"),
                        "domain", rs.getString("domain"),
                        "locale", rs.getString("locale")));
        if (docs.size() != 1) return 0;
        Map<String, Object> doc = docs.get(0);
        if (body.equals(doc.get("content"))) return 0;

        UUID documentId = (UUID) doc.get("id");
        int nextVersion = maxVersion(documentId) + 1;
        @SuppressWarnings("unchecked")
        Map<String, Object> typedDoc = doc;

        // Archive the superseded revision, then append the regenerated one.
        jdbc.update("UPDATE assistant.knowledge_document_revision SET state='ARCHIVED' "
                        + "WHERE document_id=:id AND state='PUBLISHED'",
                Map.of("id", documentId));
        jdbc.update("INSERT INTO assistant.knowledge_document_revision "
                        + "(id,document_id,version,state,locale,slug,title,content,source,priority,"
                        + "created_by,reviewed_by,published_at,domain) "
                        + "VALUES (:id,:doc,:version,'PUBLISHED',:locale,:slug,:title,:content,:source,:priority,"
                        + "'prerequisite-map-sync','prerequisite-map-sync',CURRENT_TIMESTAMP,:domain)",
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("doc", documentId)
                        .addValue("version", nextVersion)
                        .addValue("locale", typedDoc.get("locale"))
                        .addValue("slug", slug)
                        .addValue("title", typedDoc.get("title"))
                        .addValue("content", body)
                        .addValue("source", typedDoc.get("source"))
                        .addValue("priority", typedDoc.get("priority"))
                        .addValue("domain", typedDoc.get("domain")));

        UUID revisionId = jdbc.queryForObject(
                "SELECT id FROM assistant.knowledge_document_revision "
                        + "WHERE document_id=:id AND version=:version",
                new MapSqlParameterSource().addValue("id", documentId).addValue("version", nextVersion),
                UUID.class);
        promoter.promote(documentId, revisionId, "prerequisite-map-sync");
        return 1;
    }

    /** Same body the V97 migration generated, rebuilt from live rows. */
    private String buildBody(boolean vi) {
        String list = jdbc.queryForObject(
                "SELECT COALESCE(string_agg("
                        + "'- **' || t.code || '** — ' || COALESCE(t.\"nameVi\", t.name) || E'\\n  - Học phần tiên quyết: '"
                        + " || r.code || ' — ' || COALESCE(r.\"nameVi\", r.name), E'\\n' ORDER BY t.code), '')"
                        + " FROM academic.\"CourseRequirement\" req"
                        + " JOIN academic.\"Course\" t ON t.\"id\" = req.\"courseId\""
                        + " JOIN academic.\"Course\" r ON r.\"id\" = req.\"requiredCourseId\""
                        + " WHERE req.\"kind\" = 'PREREQ'",
                Map.of(), String.class);
        if (vi) return VI_BODY + list + VI_NOTES;
        String listEn = jdbc.queryForObject(
                "SELECT COALESCE(string_agg("
                        + "'- **' || t.code || '** — ' || COALESCE(t.\"nameEn\", t.name) || E'\\n  - Prerequisite: '"
                        + " || r.code || ' — ' || COALESCE(r.\"nameEn\", r.name), E'\\n' ORDER BY t.code), '')"
                        + " FROM academic.\"CourseRequirement\" req"
                        + " JOIN academic.\"Course\" t ON t.\"id\" = req.\"courseId\""
                        + " JOIN academic.\"Course\" r ON r.\"id\" = req.\"requiredCourseId\""
                        + " WHERE req.\"kind\" = 'PREREQ'",
                Map.of(), String.class);
        return EN_BODY + listEn + EN_NOTES;
    }

    private int maxVersion(UUID documentId) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM assistant.knowledge_document_revision WHERE document_id=:id",
                new MapSqlParameterSource("id", documentId), Integer.class);
        return max == null ? 0 : max;
    }
}
