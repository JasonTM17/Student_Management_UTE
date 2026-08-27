package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.web.DomainException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only CRUD and Draft -> review -> publish workflow for thesis context. */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/admin/thesis/assistant/knowledge")
public class ThesisAssistantKnowledgeAdminController {
    private final NamedParameterJdbcTemplate jdbc;

    public ThesisAssistantKnowledgeAdminController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<KnowledgeDocumentView> list(@RequestParam(required = false) String domain,
            @RequestParam(required = false) String state) {
        String suffix = "";
        MapSqlParameterSource params = p();
        if (state != null && !state.isBlank()) {
            // A document archive is a document-level governance state.  Keep it
            // visible even when its last revision was DRAFT/PUBLISHED so the
            // admin filter and the soft-archive compatibility alias agree.
            suffix += " AND CASE WHEN d.active=FALSE THEN 'ARCHIVED' ELSE COALESCE(r.state,'UNVERSIONED') END=:state";
            params.addValue("state", state.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (domain != null && !domain.isBlank()) {
            // Knowledge revisions are currently thesis/public-academic records. Keep
            // the filter explicit so a future domain column cannot broaden retrieval
            // accidentally; `THESIS` is the only writable domain in this API.
            if (!"THESIS".equalsIgnoreCase(domain) && !"ACADEMIC".equalsIgnoreCase(domain)) return List.of();
            suffix += " AND d.visibility='PUBLIC'";
        }
        return jdbc.query(latestRevisionSql(suffix) + " ORDER BY d.slug ASC", params, this::mapView);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeDocumentView get(@PathVariable UUID id) {
        List<KnowledgeDocumentView> rows = jdbc.query(latestRevisionSql(" AND d.id=:id"), p().addValue("id", id), this::mapView);
        if (rows.isEmpty()) throw notFound(id);
        return rows.get(0);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public KnowledgeRevision create(@RequestBody KnowledgeRequest request, @AuthenticationPrincipal Jwt actor) {
        validate(request);
        String owner = requireActor(actor);
        UUID document = UUID.randomUUID();
        UUID revision = UUID.randomUUID();
        int priority = priority(request);
        jdbc.update("INSERT INTO assistant.knowledge_document (id,slug,locale,title,content,source,priority,active,visibility) VALUES (:id,:slug,:locale,:title,:content,:source,:priority,TRUE,'PUBLIC')",
                p().addValue("id", document).addValue("slug", clean(request.slug())).addValue("locale", clean(request.locale()))
                        .addValue("title", clean(request.title())).addValue("content", clean(request.content()))
                        .addValue("source", clean(request.source())).addValue("priority", priority));
        insertRevision(revision, document, 1, "DRAFT", request, priority, owner);
        audit(revision, "CREATE", owner);
        return new KnowledgeRevision(document, revision, 1, "DRAFT");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public KnowledgeRevision update(@PathVariable UUID id, @RequestBody KnowledgeRequest request,
            @AuthenticationPrincipal Jwt actor) {
        validate(request);
        String owner = requireActor(actor);
        ensureDocument(id);
        lockDocument(id);
        int priority = priority(request);
        List<RevisionRow> drafts = jdbc.query(
                "SELECT id,version,created_by FROM assistant.knowledge_document_revision WHERE document_id=:document AND state='DRAFT' ORDER BY version DESC LIMIT 1 FOR UPDATE",
                p().addValue("document", id), (rs, row) -> new RevisionRow(rs.getObject("id", UUID.class), rs.getInt("version"), rs.getString("created_by")));
        UUID revision;
        int version;
        if (!drafts.isEmpty() && owner.equals(drafts.get(0).createdBy())) {
            revision = drafts.get(0).id();
            version = drafts.get(0).version();
            jdbc.update("UPDATE assistant.knowledge_document_revision SET locale=:locale,slug=:slug,title=:title,content=:content,source=:source,priority=:priority WHERE id=:id AND state='DRAFT'",
                    revisionParams(revision, request, priority));
        } else {
            version = nextVersion(id);
            revision = UUID.randomUUID();
            insertRevision(revision, id, version, "DRAFT", request, priority, owner);
        }
        audit(revision, "UPDATE", owner);
        return new KnowledgeRevision(id, revision, version, "DRAFT");
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public KnowledgeRevision submit(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        String owner = requireActor(actor);
        lockDocument(id);
        List<RevisionRow> drafts = jdbc.query(
                "SELECT id,version,created_by FROM assistant.knowledge_document_revision WHERE document_id=:id AND state='DRAFT' AND created_by=:actor ORDER BY version DESC LIMIT 1 FOR UPDATE",
                p().addValue("id", id).addValue("actor", owner), (rs, row) -> new RevisionRow(rs.getObject("id", UUID.class), rs.getInt("version"), rs.getString("created_by")));
        if (drafts.isEmpty()) throw new DomainException(HttpStatus.CONFLICT, "KNOWLEDGE_STATE_CONFLICT", "Only your draft can be submitted");
        RevisionRow draft = drafts.get(0);
        int changed = jdbc.update("UPDATE assistant.knowledge_document_revision SET state='PENDING_REVIEW' WHERE id=:revision AND state='DRAFT'",
                p().addValue("revision", draft.id()));
        if (changed != 1) throw new DomainException(HttpStatus.CONFLICT, "KNOWLEDGE_STATE_CONFLICT", "Draft is no longer available");
        audit(draft.id(), "SUBMIT", owner);
        return new KnowledgeRevision(id, draft.id(), draft.version(), "PENDING_REVIEW");
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public KnowledgeRevision publish(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        String owner = requireActor(actor);
        lockDocument(id);
        List<RevisionRow> pending = jdbc.query(
                "SELECT id,version,created_by FROM assistant.knowledge_document_revision WHERE document_id=:id AND state='PENDING_REVIEW' AND created_by<>:actor ORDER BY version DESC LIMIT 1 FOR UPDATE",
                p().addValue("id", id).addValue("actor", owner), (rs, row) -> new RevisionRow(rs.getObject("id", UUID.class), rs.getInt("version"), rs.getString("created_by")));
        if (pending.isEmpty()) throw new DomainException(HttpStatus.CONFLICT, "KNOWLEDGE_SECOND_REVIEW_REQUIRED", "A different admin must publish this revision");
        RevisionRow selected = pending.get(0);
        KnowledgePayload payload = jdbc.queryForObject(
                "SELECT slug,locale,title,content,source,priority FROM assistant.knowledge_document_revision WHERE id=:revision AND state='PENDING_REVIEW'",
                p().addValue("revision", selected.id()), (rs, row) -> new KnowledgePayload(rs.getString("slug"), rs.getString("locale"), rs.getString("title"), rs.getString("content"), rs.getString("source"), rs.getInt("priority")));
        // Re-check at the publication boundary as well as create/update. This
        // protects against rows created by an older binary or direct SQL and
        // makes publication the immutable privacy gate for retrieval.
        validatePublicKnowledge(payload.slug(), payload.locale(), payload.title(), payload.content(), payload.source());
        List<UUID> publishedRevisions = jdbc.query(
                "SELECT id FROM assistant.knowledge_document_revision WHERE document_id=:id AND state='PUBLISHED'",
                p().addValue("id", id), (rs, row) -> rs.getObject("id", UUID.class));
        jdbc.update("UPDATE assistant.knowledge_document_revision SET state='ARCHIVED' WHERE document_id=:id AND state='PUBLISHED'", p().addValue("id", id));
        for (UUID archivedRevision : publishedRevisions) {
            audit(archivedRevision, "ARCHIVE", owner);
        }
        int changed = jdbc.update("UPDATE assistant.knowledge_document_revision SET state='PUBLISHED', reviewed_by=:actor, published_at=CURRENT_TIMESTAMP WHERE id=:revision AND state='PENDING_REVIEW'",
                p().addValue("revision", selected.id()).addValue("actor", owner));
        if (changed != 1) throw new DomainException(HttpStatus.CONFLICT, "KNOWLEDGE_STATE_CONFLICT", "Revision is no longer pending");
        jdbc.update("UPDATE assistant.knowledge_document SET slug=:slug,locale=:locale,title=:title,content=:content,source=:source,priority=:priority,active=TRUE,visibility='PUBLIC',updated_at=CURRENT_TIMESTAMP WHERE id=:id",
                p().addValue("id", id).addValue("slug", payload.slug()).addValue("locale", payload.locale()).addValue("title", payload.title()).addValue("content", payload.content()).addValue("source", payload.source()).addValue("priority", payload.priority()));
        audit(selected.id(), "PUBLISH", owner);
        return new KnowledgeRevision(id, selected.id(), selected.version(), "PUBLISHED");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        String owner = requireActor(actor);
        lockDocument(id);
        List<RevisionRow> revisions = jdbc.query("SELECT id,version,created_by FROM assistant.knowledge_document_revision WHERE document_id=:id ORDER BY version DESC LIMIT 1",
                p().addValue("id", id), (rs, row) -> new RevisionRow(rs.getObject("id", UUID.class), rs.getInt("version"), rs.getString("created_by")));
        int changed = jdbc.update("UPDATE assistant.knowledge_document SET active=FALSE,archived_at=CURRENT_TIMESTAMP,archived_by=:actor,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND active=TRUE",
                p().addValue("id", id).addValue("actor", owner));
        if (changed != 1) throw notFound(id);
        if (!revisions.isEmpty()) audit(revisions.get(0).id(), "ARCHIVE", owner);
    }

    private void insertRevision(UUID revision, UUID document, int version, String state, KnowledgeRequest request, int priority, String actor) {
        jdbc.update("INSERT INTO assistant.knowledge_document_revision (id,document_id,version,state,locale,slug,title,content,source,priority,created_by) VALUES (:id,:document,:version,:state,:locale,:slug,:title,:content,:source,:priority,:actor)",
                revisionParams(revision, request, priority).addValue("document", document).addValue("version", version).addValue("state", state).addValue("actor", actor));
    }

    private void audit(UUID revision, String action, String actor) {
        jdbc.update("INSERT INTO assistant.knowledge_document_audit(id,revision_id,action,actor_id) VALUES (:id,:revision,:action,:actor)",
                p().addValue("id", UUID.randomUUID()).addValue("revision", revision).addValue("action", action).addValue("actor", actor));
    }

    private void ensureDocument(UUID id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM assistant.knowledge_document WHERE id=:id", p().addValue("id", id), Integer.class);
        if (count == null || count != 1) throw notFound(id);
    }

    private void lockDocument(UUID id) {
        List<UUID> rows = jdbc.query("SELECT id FROM assistant.knowledge_document WHERE id=:id FOR UPDATE", p().addValue("id", id), (rs, row) -> rs.getObject("id", UUID.class));
        if (rows.isEmpty()) throw notFound(id);
    }

    private int nextVersion(UUID id) {
        Integer value = jdbc.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM assistant.knowledge_document_revision WHERE document_id=:id", p().addValue("id", id), Integer.class);
        return value == null ? 1 : value;
    }

    private static String latestRevisionSql(String suffix) {
        return "SELECT d.id document_id,r.id revision_id,COALESCE(r.version,0) version,CASE WHEN d.active=FALSE THEN 'ARCHIVED' ELSE COALESCE(r.state,'UNVERSIONED') END state,COALESCE(r.locale,d.locale) locale,COALESCE(r.slug,d.slug) slug,COALESCE(r.title,d.title) title,COALESCE(r.content,d.content) content,COALESCE(r.source,d.source) source,COALESCE(r.priority,d.priority) priority,r.created_by,r.reviewed_by,r.created_at,r.published_at FROM assistant.knowledge_document d LEFT JOIN assistant.knowledge_document_revision r ON r.document_id=d.id AND r.version=(SELECT MAX(r2.version) FROM assistant.knowledge_document_revision r2 WHERE r2.document_id=d.id) WHERE 1=1" + (suffix == null ? "" : suffix);
    }

    private KnowledgeDocumentView mapView(ResultSet rs, int row) throws SQLException {
        return new KnowledgeDocumentView(rs.getObject("document_id", UUID.class), rs.getObject("revision_id", UUID.class), rs.getInt("version"), rs.getString("state"), rs.getString("locale"), rs.getString("slug"), rs.getString("title"), rs.getString("content"), rs.getString("source"), rs.getInt("priority"), rs.getString("created_by"), rs.getString("reviewed_by"), instant(rs, "created_at"), instant(rs, "published_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static MapSqlParameterSource revisionParams(UUID revision, KnowledgeRequest request, int priority) {
        return p().addValue("id", revision).addValue("locale", clean(request.locale())).addValue("slug", clean(request.slug())).addValue("title", clean(request.title())).addValue("content", clean(request.content())).addValue("source", clean(request.source())).addValue("priority", priority);
    }

    private static void validate(KnowledgeRequest request) {
        if (request == null || blank(request.slug()) || blank(request.locale()) || blank(request.title()) || blank(request.content()) || blank(request.source())) throw new IllegalArgumentException("slug, locale, title, content and source are required");
        if (request.slug().length() > 180 || request.title().length() > 500 || request.source().length() > 240 || request.content().length() > 50_000) throw new IllegalArgumentException("knowledge fields exceed allowed length");
        if (!request.locale().equals("vi") && !request.locale().equals("en") && !request.locale().equals("both")) throw new IllegalArgumentException("locale must be vi, en or both");
        if (request.priority() != null && (request.priority() < 1 || request.priority() > 1000)) throw new IllegalArgumentException("priority must be between 1 and 1000");
        validatePublicKnowledge(request.slug(), request.locale(), request.title(), request.content(), request.source());
    }

    private static void validatePublicKnowledge(String... values) {
        for (String value : values) {
            AssistantInputGuard.GuardResult result = AssistantInputGuard.inspectPublicKnowledge(value);
            if (!result.allowed()) {
                throw new DomainException(HttpStatus.BAD_REQUEST, "KNOWLEDGE_PRIVACY_REJECTED",
                        "Knowledge content contains a prohibited personal or unsafe pattern");
            }
        }
    }

    private static int priority(KnowledgeRequest request) { return request.priority() == null ? 100 : request.priority(); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String requireActor(Jwt actor) { String subject = actor == null ? null : actor.getSubject(); if (blank(subject)) throw new DomainException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"); return subject; }
    private static DomainException notFound(UUID id) { return new DomainException(HttpStatus.NOT_FOUND, "KNOWLEDGE_NOT_FOUND", "Knowledge document not found: " + id); }
    private static MapSqlParameterSource p() { return new MapSqlParameterSource(); }

    private record RevisionRow(UUID id, int version, String createdBy) { }
    private record KnowledgePayload(String slug, String locale, String title, String content, String source, int priority) { }
    public record KnowledgeRequest(String slug, String locale, String title, String content, String source, Integer priority) { }
    public record KnowledgeRevision(UUID documentId, UUID revisionId, int version, String state) { }
    public record KnowledgeDocumentView(UUID documentId, UUID revisionId, int version, String state, String locale, String slug, String title, String content, String source, int priority, String createdBy, String reviewedBy, Instant createdAt, Instant publishedAt) { }
}
