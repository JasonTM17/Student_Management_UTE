package io.campuscore.restfulapi.thesis.assistant.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import io.campuscore.restfulapi.thesis.assistant.config.AssistantDatabaseConfiguration;

/**
 * Statements behind the prerequisite-map sync: live {@code
 * academic."CourseRequirement"} aggregation, the governed knowledge document
 * reads, and the PUBLISHED-revision rewrite the sync performs on drift.
 */
@Repository
@Profile("persistence")
public class ThesisAssistantPrerequisiteMapRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ThesisAssistantPrerequisiteMapRepository(
            @Qualifier(AssistantDatabaseConfiguration.JDBC_TEMPLATE) NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The aggregated Vietnamese prerequisite lines, or an empty string. */
    public String viRequirementList() {
        return jdbc.queryForObject(
                "SELECT COALESCE(string_agg("
                        + "'- **' || t.code || '** — ' || COALESCE(t.\"nameVi\", t.name) || E'\\n  - Học phần tiên quyết: '"
                        + " || r.code || ' — ' || COALESCE(r.\"nameVi\", r.name), E'\\n' ORDER BY t.code), '')"
                        + " FROM academic.\"CourseRequirement\" req"
                        + " JOIN academic.\"Course\" t ON t.\"id\" = req.\"courseId\""
                        + " JOIN academic.\"Course\" r ON r.\"id\" = req.\"requiredCourseId\""
                        + " WHERE req.\"kind\" = 'PREREQ'",
                Map.of(), String.class);
    }

    /** The aggregated English prerequisite lines, or an empty string. */
    public String enRequirementList() {
        return jdbc.queryForObject(
                "SELECT COALESCE(string_agg("
                        + "'- **' || t.code || '** — ' || COALESCE(t.\"nameEn\", t.name) || E'\\n  - Prerequisite: '"
                        + " || r.code || ' — ' || COALESCE(r.\"nameEn\", r.name), E'\\n' ORDER BY t.code), '')"
                        + " FROM academic.\"CourseRequirement\" req"
                        + " JOIN academic.\"Course\" t ON t.\"id\" = req.\"courseId\""
                        + " JOIN academic.\"Course\" r ON r.\"id\" = req.\"requiredCourseId\""
                        + " WHERE req.\"kind\" = 'PREREQ'",
                Map.of(), String.class);
    }

    public List<Map<String, Object>> findActiveDocuments(String slug) {
        return jdbc.query(
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
    }

    public int maxVersion(UUID documentId) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM assistant.knowledge_document_revision WHERE document_id=:id",
                new MapSqlParameterSource("id", documentId), Integer.class);
        return max == null ? 0 : max;
    }

    public void archivePublishedRevisions(UUID documentId) {
        jdbc.update("UPDATE assistant.knowledge_document_revision SET state='ARCHIVED' "
                        + "WHERE document_id=:id AND state='PUBLISHED'",
                Map.of("id", documentId));
    }

    public void insertPublishedRevision(UUID revisionId, UUID documentId, int nextVersion,
            String slug, String body, Map<String, Object> doc) {
        jdbc.update("INSERT INTO assistant.knowledge_document_revision "
                        + "(id,document_id,version,state,locale,slug,title,content,source,priority,"
                        + "created_by,reviewed_by,published_at,domain) "
                        + "VALUES (:id,:doc,:version,'PUBLISHED',:locale,:slug,:title,:content,:source,:priority,"
                        + "'prerequisite-map-sync','prerequisite-map-sync',CURRENT_TIMESTAMP,:domain)",
                new MapSqlParameterSource()
                        .addValue("id", revisionId)
                        .addValue("doc", documentId)
                        .addValue("version", nextVersion)
                        .addValue("locale", doc.get("locale"))
                        .addValue("slug", slug)
                        .addValue("title", doc.get("title"))
                        .addValue("content", body)
                        .addValue("source", doc.get("source"))
                        .addValue("priority", doc.get("priority"))
                        .addValue("domain", doc.get("domain")));
    }

    public UUID findRevisionId(UUID documentId, int version) {
        return jdbc.queryForObject(
                "SELECT id FROM assistant.knowledge_document_revision "
                        + "WHERE document_id=:id AND version=:version",
                new MapSqlParameterSource().addValue("id", documentId).addValue("version", version),
                UUID.class);
    }

    public void updateDocumentContent(UUID documentId, String body) {
        jdbc.update("UPDATE assistant.knowledge_document SET content=:content,updated_at=CURRENT_TIMESTAMP "
                        + "WHERE id=:id",
                new MapSqlParameterSource().addValue("id", documentId).addValue("content", body));
    }
}
