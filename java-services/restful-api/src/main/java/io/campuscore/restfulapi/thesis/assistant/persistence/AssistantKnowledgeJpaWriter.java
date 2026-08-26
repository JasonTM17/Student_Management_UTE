package io.campuscore.restfulapi.thesis.assistant.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.campuscore.restfulapi.thesis.assistant.AssistantInputGuard;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-only governance writer for assistant knowledge.
 *
 * The writer owns the document/revision/audit boundary. Callers must pass the
 * authenticated actor explicitly; no owner or review decision is inferred
 * from a database row. Pessimistic locks are acquired on the document before
 * its revisions, matching the existing JDBC governance lock order.
 */
@Service
@Profile("persistence")
public class AssistantKnowledgeJpaWriter {

    private final AssistantKnowledgeDocumentJpaRepository documents;
    private final AssistantKnowledgeRevisionJpaRepository revisions;
    private final AssistantKnowledgeAuditJpaRepository audits;

    public AssistantKnowledgeJpaWriter(AssistantKnowledgeDocumentJpaRepository documents,
            AssistantKnowledgeRevisionJpaRepository revisions,
            AssistantKnowledgeAuditJpaRepository audits) {
        this.documents = documents;
        this.revisions = revisions;
        this.audits = audits;
    }

    @Transactional
    public RevisionSnapshot create(KnowledgeCommand command, String actor) {
        KnowledgeCommand clean = validate(command, actor);
        if (documents.existsBySlug(clean.slug())) throw conflict("KNOWLEDGE_SLUG_CONFLICT");
        Instant now = Instant.now();
        UUID documentId = UUID.randomUUID();
        AssistantKnowledgeDocumentEntity document = AssistantKnowledgeDocumentEntity.draft(
                documentId, clean.slug(), clean.locale(), clean.title(), clean.content(),
                clean.source(), clean.priority(), now);
        documents.save(document);
        AssistantKnowledgeRevisionEntity revision = AssistantKnowledgeRevisionEntity.draft(
                UUID.randomUUID(), documentId, 1, clean.locale(), clean.slug(), clean.title(),
                clean.content(), clean.source(), clean.priority(), clean.actor(), now);
        revisions.save(revision);
        audit(revision, "CREATE", clean.actor(), now);
        return snapshot(revision);
    }

    @Transactional
    public RevisionSnapshot update(UUID documentId, KnowledgeCommand command, String actor) {
        KnowledgeCommand clean = validate(command, actor);
        AssistantKnowledgeDocumentEntity document = lockDocument(documentId);
        requireActive(document);
        if (documents.existsBySlugAndIdNot(clean.slug(), documentId)) {
            throw conflict("KNOWLEDGE_SLUG_CONFLICT");
        }
        Instant now = Instant.now();
        List<AssistantKnowledgeRevisionEntity> drafts = revisions.findOwnDraftsForUpdate(documentId, clean.actor());
        AssistantKnowledgeRevisionEntity revision;
        if (!drafts.isEmpty()) {
            revision = drafts.get(0);
            revision.replace(clean.locale(), clean.slug(), clean.title(), clean.content(), clean.source(), clean.priority());
        } else {
            int version = revisions.nextVersionBase(documentId) + 1;
            revision = AssistantKnowledgeRevisionEntity.draft(
                    UUID.randomUUID(), documentId, version, clean.locale(), clean.slug(), clean.title(),
                    clean.content(), clean.source(), clean.priority(), clean.actor(), now);
            revisions.save(revision);
        }
        // Do not copy draft content into the base row: the legacy lexical reader
        // still reads the base projection, so doing so would publish unreviewed
        // text before the second-review gate. The base row changes only at publish.
        audits.save(AssistantKnowledgeAuditEntity.record(
                UUID.randomUUID(), revision.getId(), "UPDATE", clean.actor(), null, now));
        return snapshot(revision);
    }

    @Transactional
    public RevisionSnapshot submit(UUID documentId, String actor) {
        AssistantKnowledgeDocumentEntity document = lockDocument(documentId);
        requireActive(document);
        List<AssistantKnowledgeRevisionEntity> drafts = revisions.findOwnDraftsForUpdate(documentId, requireActor(actor));
        if (drafts.isEmpty()) throw conflict("KNOWLEDGE_STATE_CONFLICT");
        AssistantKnowledgeRevisionEntity revision = drafts.get(0);
        revision.submit();
        audit(revision, "SUBMIT", actor, Instant.now());
        return snapshot(revision);
    }

    @Transactional
    public RevisionSnapshot publish(UUID documentId, String actor) {
        AssistantKnowledgeDocumentEntity document = lockDocument(documentId);
        requireActive(document);
        String reviewer = requireActor(actor);
        List<AssistantKnowledgeRevisionEntity> pending = revisions.findPendingForOtherReviewer(documentId, reviewer);
        if (pending.isEmpty()) throw conflict("KNOWLEDGE_SECOND_REVIEW_REQUIRED");
        AssistantKnowledgeRevisionEntity selected = pending.get(0);
        validatePublic(selected.getSlug(), selected.getLocale(), selected.getTitle(),
                selected.getContent(), selected.getSource());
        if (documents.existsBySlugAndIdNot(selected.getSlug(), documentId)) {
            throw conflict("KNOWLEDGE_SLUG_CONFLICT");
        }
        Instant now = Instant.now();
        for (AssistantKnowledgeRevisionEntity published : revisions.findPublishedForUpdate(documentId)) {
            published.archive();
            audit(published, "ARCHIVE", reviewer, now);
        }
        // The database enforces one PUBLISHED revision per document. Flush the
        // archived predecessor before marking its replacement as PUBLISHED so
        // Hibernate statement ordering cannot transiently violate that index.
        revisions.flush();
        selected.publish(reviewer, now);
        document.replace(selected.getSlug(), selected.getLocale(), selected.getTitle(),
                selected.getContent(), selected.getSource(), selected.getPriority(), now);
        document.publish(now);
        audits.save(AssistantKnowledgeAuditEntity.record(
                UUID.randomUUID(), selected.getId(), "PUBLISH", reviewer, null, now));
        return snapshot(selected);
    }

    @Transactional
    public void archive(UUID documentId, String actor) {
        AssistantKnowledgeDocumentEntity document = lockDocument(documentId);
        String owner = requireActor(actor);
        Instant now = Instant.now();
        document.archive(owner, now);
        revisions.findFirstByDocumentIdOrderByVersionDesc(documentId)
                .ifPresent(revision -> audit(revision, "ARCHIVE", owner, now));
    }

    private AssistantKnowledgeDocumentEntity lockDocument(UUID documentId) {
        if (documentId == null) throw notFound();
        return documents.findLockedById(documentId).orElseThrow(this::notFound);
    }

    private static void requireActive(AssistantKnowledgeDocumentEntity document) {
        if (!document.isActive()) throw conflict("KNOWLEDGE_ARCHIVED");
    }

    private void audit(AssistantKnowledgeRevisionEntity revision, String action, String actor, Instant now) {
        audits.save(AssistantKnowledgeAuditEntity.record(
                UUID.randomUUID(), revision.getId(), action, requireActor(actor), null, now));
    }

    private static RevisionSnapshot snapshot(AssistantKnowledgeRevisionEntity revision) {
        return new RevisionSnapshot(revision.getDocumentId(), revision.getId(), revision.getVersion(),
                revision.getState());
    }

    private static KnowledgeCommand validate(KnowledgeCommand command, String actor) {
        if (command == null) throw new IllegalArgumentException("knowledge request is required");
        String owner = requireActor(actor);
        String slug = clean(command.slug());
        String locale = clean(command.locale());
        String title = clean(command.title());
        String content = clean(command.content());
        String source = clean(command.source());
        if (slug.isBlank() || locale.isBlank() || title.isBlank() || content.isBlank() || source.isBlank()) {
            throw new IllegalArgumentException("slug, locale, title, content and source are required");
        }
        if (slug.length() > 180 || title.length() > 500 || source.length() > 240 || content.length() > 50_000) {
            throw new IllegalArgumentException("knowledge fields exceed allowed length");
        }
        if (!(locale.equals("vi") || locale.equals("en") || locale.equals("both"))) {
            throw new IllegalArgumentException("locale must be vi, en or both");
        }
        int priority = command.priority() == null ? 100 : command.priority();
        if (priority < 1 || priority > 1000) throw new IllegalArgumentException("priority must be between 1 and 1000");
        validatePublic(slug, locale, title, content, source);
        return new KnowledgeCommand(slug, locale, title, content, source, priority, owner);
    }

    static void validatePublic(String... values) {
        for (String value : values) {
            if (!AssistantInputGuard.inspectPublicKnowledge(value).allowed()) {
                throw new KnowledgePersistenceException("KNOWLEDGE_PRIVACY_REJECTED");
            }
        }
    }

    private static void validatePublic(String slug, String locale, String title, String content, String source) {
        validatePublic(new String[] { slug, locale, title, content, source });
    }

    private static String requireActor(String actor) {
        if (actor == null || actor.isBlank()) throw new KnowledgePersistenceException("UNAUTHENTICATED");
        return actor.trim();
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    private static KnowledgePersistenceException conflict(String code) {
        return new KnowledgePersistenceException(code);
    }

    private KnowledgePersistenceException notFound() {
        return new KnowledgePersistenceException("KNOWLEDGE_NOT_FOUND");
    }

    public record KnowledgeCommand(String slug, String locale, String title, String content,
            String source, Integer priority, String actor) {
    }

    public record RevisionSnapshot(UUID documentId, UUID revisionId, int version, String state) {
    }

    public static final class KnowledgePersistenceException extends RuntimeException {
        private final String code;

        public KnowledgePersistenceException(String code) {
            super(code);
            this.code = code;
        }

        public String code() { return code; }
    }
}
