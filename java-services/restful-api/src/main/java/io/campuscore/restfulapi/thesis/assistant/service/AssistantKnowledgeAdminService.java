package io.campuscore.restfulapi.thesis.assistant.service;

import io.campuscore.restfulapi.exception.DomainException;
import io.campuscore.restfulapi.thesis.assistant.dto.KnowledgeDocumentView;
import io.campuscore.restfulapi.thesis.assistant.dto.KnowledgeRequest;
import io.campuscore.restfulapi.thesis.assistant.dto.KnowledgeRevision;
import io.campuscore.restfulapi.thesis.assistant.model.KnowledgeRevisionPayload;
import io.campuscore.restfulapi.thesis.assistant.model.KnowledgeRevisionRow;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.campuscore.restfulapi.thesis.assistant.config.AssistantDatabaseConfiguration;
import io.campuscore.restfulapi.thesis.assistant.guard.AssistantInputGuard;
import io.campuscore.restfulapi.thesis.assistant.rls.AssistantRlsBoundary;
import io.campuscore.restfulapi.thesis.assistant.repository.ThesisAssistantKnowledgeAdminRepository;

/**
 * Admin-only CRUD and Draft -> review -> publish workflow for public campus
 * knowledge. Owns the transaction boundary and the statement order; every
 * statement lives in {@link ThesisAssistantKnowledgeAdminRepository} and runs
 * inside the {@code ADMIN_GOVERNANCE} RLS boundary marked on this class.
 */
@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "assistant.knowledge", name = "authority-mode", havingValue = "sql", matchIfMissing = true)
@AssistantRlsBoundary(access = AssistantRlsBoundary.Access.ADMIN_GOVERNANCE)
public class AssistantKnowledgeAdminService {

    private final ThesisAssistantKnowledgeAdminRepository repository;
    private final SqlKnowledgeReleasePromoter releasePromoter;

    public AssistantKnowledgeAdminService(
            ThesisAssistantKnowledgeAdminRepository repository,
            SqlKnowledgeReleasePromoter releasePromoter) {
        this.repository = repository;
        this.releasePromoter = releasePromoter;
    }

    public List<KnowledgeDocumentView> list(String domain, String state) {
        String normalizedDomain = null;
        if (domain != null && !domain.isBlank()) {
            if (!AssistantKnowledgeDomains.isAllowed(domain)) return List.of();
            normalizedDomain = AssistantKnowledgeDomains.normalize(domain);
        }
        return repository.listDocuments(state, normalizedDomain);
    }

    public KnowledgeDocumentView get(UUID id) {
        List<KnowledgeDocumentView> rows = repository.findDocumentView(id);
        if (rows.isEmpty()) throw notFound(id);
        return rows.get(0);
    }

    @Transactional(transactionManager = AssistantDatabaseConfiguration.TRANSACTION_MANAGER)
    public KnowledgeRevision create(KnowledgeRequest request, Jwt actor) {
        validate(request);
        String owner = requireActor(actor);
        UUID document = UUID.randomUUID();
        UUID revision = UUID.randomUUID();
        int priority = priority(request);
        repository.insertDocument(document, request, AssistantKnowledgeDomains.normalize(request.domain()), priority);
        repository.insertRevision(revision, document, 1, "DRAFT", request,
                AssistantKnowledgeDomains.normalize(request.domain()), priority, owner);
        repository.insertAudit(revision, "CREATE", owner);
        return new KnowledgeRevision(document, revision, 1, "DRAFT");
    }

    @Transactional(transactionManager = AssistantDatabaseConfiguration.TRANSACTION_MANAGER)
    public KnowledgeRevision update(UUID id, KnowledgeRequest request, Jwt actor) {
        validate(request);
        String owner = requireActor(actor);
        ensureDocument(id);
        lockDocument(id);
        int priority = priority(request);
        List<KnowledgeRevisionRow> drafts = repository.findLatestDraftForUpdate(id);
        UUID revision;
        int version;
        if (!drafts.isEmpty() && owner.equals(drafts.get(0).createdBy())) {
            revision = drafts.get(0).id();
            version = drafts.get(0).version();
            repository.updateDraftRevision(revision, request,
                    AssistantKnowledgeDomains.normalize(request.domain()), priority);
        } else {
            version = repository.nextVersion(id);
            revision = UUID.randomUUID();
            repository.insertRevision(revision, id, version, "DRAFT", request,
                    AssistantKnowledgeDomains.normalize(request.domain()), priority, owner);
        }
        repository.insertAudit(revision, "UPDATE", owner);
        return new KnowledgeRevision(id, revision, version, "DRAFT");
    }

    @Transactional(transactionManager = AssistantDatabaseConfiguration.TRANSACTION_MANAGER)
    public KnowledgeRevision submit(UUID id, Jwt actor) {
        String owner = requireActor(actor);
        lockDocument(id);
        List<KnowledgeRevisionRow> drafts = repository.findOwnedDraftForUpdate(id, owner);
        if (drafts.isEmpty()) throw new DomainException(HttpStatus.CONFLICT, "KNOWLEDGE_STATE_CONFLICT", "Only your draft can be submitted");
        KnowledgeRevisionRow draft = drafts.get(0);
        int changed = repository.markRevisionPending(draft.id());
        if (changed != 1) throw new DomainException(HttpStatus.CONFLICT, "KNOWLEDGE_STATE_CONFLICT", "Draft is no longer available");
        repository.insertAudit(draft.id(), "SUBMIT", owner);
        return new KnowledgeRevision(id, draft.id(), draft.version(), "PENDING_REVIEW");
    }

    @Transactional(transactionManager = AssistantDatabaseConfiguration.TRANSACTION_MANAGER)
    public KnowledgeRevision publish(UUID id, Jwt actor) {
        String owner = requireActor(actor);
        lockDocument(id);
        Boolean active = repository.findDocumentActive(id);
        if (Boolean.FALSE.equals(active)) {
            throw new DomainException(HttpStatus.CONFLICT, "KNOWLEDGE_STATE_CONFLICT", "Cannot publish an archived document");
        }
        List<KnowledgeRevisionRow> pending = repository.findLatestPendingForUpdate(id, owner);
        if (pending.isEmpty()) throw new DomainException(HttpStatus.CONFLICT, "KNOWLEDGE_SECOND_REVIEW_REQUIRED", "A different admin must publish this revision");
        KnowledgeRevisionRow selected = pending.get(0);
        KnowledgeRevisionPayload payload = repository.findRevisionPayload(selected.id());
        // Re-check at the publication boundary as well as create/update. This
        // protects against rows created by an older binary or direct SQL and
        // makes publication the immutable privacy gate for retrieval.
        validatePublicKnowledge(payload.slug(), payload.locale(), payload.title(), payload.content(), payload.source());
        List<UUID> publishedRevisions = repository.findPublishedRevisionIds(id);
        repository.archivePublishedRevisions(id);
        for (UUID archivedRevision : publishedRevisions) {
            repository.insertAudit(archivedRevision, "ARCHIVE", owner);
        }
        int changed = repository.publishRevision(selected.id(), owner);
        if (changed != 1) throw new DomainException(HttpStatus.CONFLICT, "KNOWLEDGE_STATE_CONFLICT", "Revision is no longer pending");
        repository.updateDocumentFromRevision(id, payload);
        repository.insertAudit(selected.id(), "PUBLISH", owner);
        releasePromoter.promote(id, selected.id(), owner);
        return new KnowledgeRevision(id, selected.id(), selected.version(), "PUBLISHED");
    }

    @Transactional(transactionManager = AssistantDatabaseConfiguration.TRANSACTION_MANAGER)
    public void delete(UUID id, Jwt actor) {
        String owner = requireActor(actor);
        lockDocument(id);
        List<KnowledgeRevisionRow> revisions = repository.findLatestRevision(id);
        int changed = repository.archiveDocument(id, owner);
        if (changed != 1) throw notFound(id);
        if (!revisions.isEmpty()) repository.insertAudit(revisions.get(0).id(), "ARCHIVE", owner);
        releasePromoter.promote(id, null, owner);
    }

    private void ensureDocument(UUID id) {
        Integer count = repository.countDocument(id);
        if (count == null || count != 1) throw notFound(id);
    }

    private void lockDocument(UUID id) {
        List<UUID> rows = repository.lockDocumentForUpdate(id);
        if (rows.isEmpty()) throw notFound(id);
    }

    public static void validate(KnowledgeRequest request) {
        if (request == null || blank(request.slug()) || blank(request.locale()) || blank(request.title()) || blank(request.content()) || blank(request.source())) throw new IllegalArgumentException("slug, locale, title, content and source are required");
        if (request.slug().length() > 180 || request.title().length() > 500 || request.source().length() > 240 || request.content().length() > 50_000) throw new IllegalArgumentException("knowledge fields exceed allowed length");
        if (!request.locale().equals("vi") && !request.locale().equals("en") && !request.locale().equals("both")) throw new IllegalArgumentException("locale must be vi, en or both");
        if (request.priority() != null && (request.priority() < 1 || request.priority() > 1000)) throw new IllegalArgumentException("priority must be between 1 and 1000");
        if (!AssistantKnowledgeDomains.isAllowed(request.domain())) throw new IllegalArgumentException("domain is not supported");
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
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    static String requireActor(Jwt actor) { String subject = actor == null ? null : actor.getSubject(); if (blank(subject)) throw new DomainException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"); return subject; }
    private static DomainException notFound(UUID id) { return new DomainException(HttpStatus.NOT_FOUND, "KNOWLEDGE_NOT_FOUND", "Knowledge document not found: " + id); }
}
