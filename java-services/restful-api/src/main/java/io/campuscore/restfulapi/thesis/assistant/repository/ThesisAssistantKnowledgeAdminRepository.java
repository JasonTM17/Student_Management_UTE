package io.campuscore.restfulapi.thesis.assistant.repository;

import io.campuscore.restfulapi.thesis.assistant.dto.KnowledgeDocumentView;
import io.campuscore.restfulapi.thesis.assistant.dto.KnowledgeRequest;
import io.campuscore.restfulapi.thesis.assistant.model.KnowledgeRevisionPayload;
import io.campuscore.restfulapi.thesis.assistant.model.KnowledgeRevisionRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import io.campuscore.restfulapi.thesis.assistant.config.AssistantDatabaseConfiguration;

/**
 * SQL for the admin knowledge governance workflow (document, revision and
 * audit tables). Every statement runs on the assistant datasource inside the
 * caller's transaction; the {@code ...ForUpdate} methods take document or
 * revision row locks and therefore demand an ambient transaction.
 */
@Repository
@Profile("persistence")
public class ThesisAssistantKnowledgeAdminRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ThesisAssistantKnowledgeAdminRepository(
            @Qualifier(AssistantDatabaseConfiguration.JDBC_TEMPLATE) NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<KnowledgeDocumentView> listDocuments(String state, String normalizedDomain) {
        String suffix = "";
        MapSqlParameterSource params = p();
        if (state != null && !state.isBlank()) {
            // A document archive is a document-level governance state.  Keep it
            // visible even when its last revision was DRAFT/PUBLISHED so the
            // admin filter and the soft-archive compatibility alias agree.
            suffix += " AND CASE WHEN d.active=FALSE THEN 'ARCHIVED' ELSE COALESCE(r.state,'UNVERSIONED') END=:state";
            params.addValue("state", state.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (normalizedDomain != null) {
            suffix += " AND COALESCE(r.domain,d.domain)=:domain";
            params.addValue("domain", normalizedDomain);
        }
        return jdbc.query(latestRevisionSql(suffix, false) + " ORDER BY d.slug ASC", params, this::mapView);
    }

    public List<KnowledgeDocumentView> findDocumentView(UUID id) {
        return jdbc.query(latestRevisionSql(" AND d.id=:id", true), p().addValue("id", id), this::mapView);
    }

    public void insertDocument(UUID document, KnowledgeRequest request, String normalizedDomain, int priority) {
        jdbc.update("INSERT INTO assistant.knowledge_document (id,slug,locale,title,content,source,domain,priority,active,visibility) VALUES (:id,:slug,:locale,:title,:content,:source,:domain,:priority,TRUE,'PUBLIC')",
                p().addValue("id", document).addValue("slug", clean(request.slug())).addValue("locale", clean(request.locale()))
                        .addValue("title", clean(request.title())).addValue("content", clean(request.content()))
                        .addValue("source", clean(request.source())).addValue("domain", normalizedDomain).addValue("priority", priority));
    }

    public void insertRevision(UUID revision, UUID document, int version, String state,
            KnowledgeRequest request, String normalizedDomain, int priority, String actor) {
        jdbc.update("INSERT INTO assistant.knowledge_document_revision (id,document_id,version,state,domain,locale,slug,title,content,source,priority,created_by) VALUES (:id,:document,:version,:state,:domain,:locale,:slug,:title,:content,:source,:priority,:actor)",
                revisionParams(revision, request, normalizedDomain, priority).addValue("document", document).addValue("version", version).addValue("state", state).addValue("actor", actor));
    }

    public int updateDraftRevision(UUID revision, KnowledgeRequest request, String normalizedDomain, int priority) {
        return jdbc.update("UPDATE assistant.knowledge_document_revision SET domain=:domain,locale=:locale,slug=:slug,title=:title,content=:content,source=:source,priority=:priority WHERE id=:id AND state='DRAFT'",
                revisionParams(revision, request, normalizedDomain, priority));
    }

    public void insertAudit(UUID revision, String action, String actor) {
        jdbc.update("INSERT INTO assistant.knowledge_document_audit(id,revision_id,action,actor_id) VALUES (:id,:revision,:action,:actor)",
                p().addValue("id", UUID.randomUUID()).addValue("revision", revision).addValue("action", action).addValue("actor", actor));
    }

    public Integer countDocument(UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM assistant.knowledge_document WHERE id=:id", p().addValue("id", id), Integer.class);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> lockDocumentForUpdate(UUID id) {
        return jdbc.query("SELECT id FROM assistant.knowledge_document WHERE id=:id FOR UPDATE", p().addValue("id", id), (rs, row) -> rs.getObject("id", UUID.class));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<KnowledgeRevisionRow> findLatestDraftForUpdate(UUID documentId) {
        return jdbc.query(
                "SELECT id,version,created_by FROM assistant.knowledge_document_revision WHERE document_id=:document AND state='DRAFT' ORDER BY version DESC LIMIT 1 FOR UPDATE",
                p().addValue("document", documentId),
                (rs, row) -> new KnowledgeRevisionRow(rs.getObject("id", UUID.class), rs.getInt("version"), rs.getString("created_by")));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<KnowledgeRevisionRow> findOwnedDraftForUpdate(UUID documentId, String actor) {
        return jdbc.query(
                "SELECT id,version,created_by FROM assistant.knowledge_document_revision WHERE document_id=:id AND state='DRAFT' AND created_by=:actor ORDER BY version DESC LIMIT 1 FOR UPDATE",
                p().addValue("id", documentId).addValue("actor", actor),
                (rs, row) -> new KnowledgeRevisionRow(rs.getObject("id", UUID.class), rs.getInt("version"), rs.getString("created_by")));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<KnowledgeRevisionRow> findLatestPendingForUpdate(UUID documentId, String actor) {
        return jdbc.query(
                "SELECT id,version,created_by FROM assistant.knowledge_document_revision WHERE document_id=:id AND state='PENDING_REVIEW' AND created_by<>:actor ORDER BY version DESC LIMIT 1 FOR UPDATE",
                p().addValue("id", documentId).addValue("actor", actor),
                (rs, row) -> new KnowledgeRevisionRow(rs.getObject("id", UUID.class), rs.getInt("version"), rs.getString("created_by")));
    }

    public Boolean findDocumentActive(UUID id) {
        return jdbc.queryForObject(
                "SELECT active FROM assistant.knowledge_document WHERE id=:id",
                p().addValue("id", id),
                Boolean.class);
    }

    public KnowledgeRevisionPayload findRevisionPayload(UUID revisionId) {
        return jdbc.queryForObject(
                "SELECT domain,slug,locale,title,content,source,priority FROM assistant.knowledge_document_revision WHERE id=:revision AND state='PENDING_REVIEW'",
                p().addValue("revision", revisionId),
                (rs, row) -> new KnowledgeRevisionPayload(rs.getString("slug"), rs.getString("locale"), rs.getString("title"), rs.getString("content"), rs.getString("source"), rs.getInt("priority"), rs.getString("domain")));
    }

    public List<UUID> findPublishedRevisionIds(UUID documentId) {
        return jdbc.query(
                "SELECT id FROM assistant.knowledge_document_revision WHERE document_id=:id AND state='PUBLISHED'",
                p().addValue("id", documentId), (rs, row) -> rs.getObject("id", UUID.class));
    }

    public int archivePublishedRevisions(UUID documentId) {
        return jdbc.update("UPDATE assistant.knowledge_document_revision SET state='ARCHIVED' WHERE document_id=:id AND state='PUBLISHED'", p().addValue("id", documentId));
    }

    public int publishRevision(UUID revisionId, String actor) {
        return jdbc.update("UPDATE assistant.knowledge_document_revision SET state='PUBLISHED', reviewed_by=:actor, published_at=CURRENT_TIMESTAMP WHERE id=:revision AND state='PENDING_REVIEW'",
                p().addValue("revision", revisionId).addValue("actor", actor));
    }

    public void updateDocumentFromRevision(UUID id, KnowledgeRevisionPayload payload) {
        jdbc.update("UPDATE assistant.knowledge_document SET domain=:domain,slug=:slug,locale=:locale,title=:title,content=:content,source=:source,priority=:priority,active=TRUE,visibility='PUBLIC',updated_at=CURRENT_TIMESTAMP WHERE id=:id",
                p().addValue("id", id).addValue("domain", payload.domain()).addValue("slug", payload.slug()).addValue("locale", payload.locale()).addValue("title", payload.title()).addValue("content", payload.content()).addValue("source", payload.source()).addValue("priority", payload.priority()));
    }

    public List<KnowledgeRevisionRow> findLatestRevision(UUID documentId) {
        return jdbc.query("SELECT id,version,created_by FROM assistant.knowledge_document_revision WHERE document_id=:id ORDER BY version DESC LIMIT 1",
                p().addValue("id", documentId),
                (rs, row) -> new KnowledgeRevisionRow(rs.getObject("id", UUID.class), rs.getInt("version"), rs.getString("created_by")));
    }

    public int archiveDocument(UUID id, String actor) {
        return jdbc.update("UPDATE assistant.knowledge_document SET active=FALSE,archived_at=CURRENT_TIMESTAMP,archived_by=:actor,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND active=TRUE",
                p().addValue("id", id).addValue("actor", actor));
    }

    public int markRevisionPending(UUID revisionId) {
        return jdbc.update("UPDATE assistant.knowledge_document_revision SET state='PENDING_REVIEW' WHERE id=:revision AND state='DRAFT'",
                p().addValue("revision", revisionId));
    }

    public int nextVersion(UUID id) {
        Integer value = jdbc.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM assistant.knowledge_document_revision WHERE document_id=:id", p().addValue("id", id), Integer.class);
        return value == null ? 1 : value;
    }

    private static String latestRevisionSql(String suffix, boolean includeContent) {
        String contentField = includeContent ? "COALESCE(r.content,d.content) content" : "'' content";
        return "SELECT d.id document_id,r.id revision_id,COALESCE(r.version,0) version,CASE WHEN d.active=FALSE THEN 'ARCHIVED' ELSE COALESCE(r.state,'UNVERSIONED') END state,COALESCE(r.domain,d.domain,'THESIS') domain,COALESCE(r.locale,d.locale) locale,COALESCE(r.slug,d.slug) slug,COALESCE(r.title,d.title) title," + contentField + ",COALESCE(r.source,d.source) source,COALESCE(r.priority,d.priority) priority,r.created_by,r.reviewed_by,r.created_at,r.published_at FROM assistant.knowledge_document d LEFT JOIN assistant.knowledge_document_revision r ON r.document_id=d.id AND r.version=(SELECT MAX(r2.version) FROM assistant.knowledge_document_revision r2 WHERE r2.document_id=d.id) WHERE 1=1" + (suffix == null ? "" : suffix);
    }

    private KnowledgeDocumentView mapView(ResultSet rs, int row) throws SQLException {
        return new KnowledgeDocumentView(rs.getObject("document_id", UUID.class), rs.getObject("revision_id", UUID.class), rs.getInt("version"), rs.getString("state"), rs.getString("domain"), rs.getString("locale"), rs.getString("slug"), rs.getString("title"), rs.getString("content"), rs.getString("source"), rs.getInt("priority"), rs.getString("created_by"), rs.getString("reviewed_by"), instant(rs, "created_at"), instant(rs, "published_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static MapSqlParameterSource revisionParams(UUID revision, KnowledgeRequest request, String normalizedDomain, int priority) {
        return p().addValue("id", revision).addValue("domain", normalizedDomain).addValue("locale", clean(request.locale())).addValue("slug", clean(request.slug())).addValue("title", clean(request.title())).addValue("content", clean(request.content())).addValue("source", clean(request.source())).addValue("priority", priority);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static MapSqlParameterSource p() { return new MapSqlParameterSource(); }
}
