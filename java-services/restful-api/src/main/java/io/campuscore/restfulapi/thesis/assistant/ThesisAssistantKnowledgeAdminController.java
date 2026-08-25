package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantKnowledgeDocumentEntity;
import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantKnowledgeDocumentJpaRepository;
import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantKnowledgeJpaWriter;
import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantKnowledgeJpaWriter.KnowledgeCommand;
import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantKnowledgeJpaWriter.KnowledgePersistenceException;
import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantKnowledgeJpaWriter.RevisionSnapshot;
import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantKnowledgeRevisionEntity;
import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantKnowledgeRevisionJpaRepository;
import io.campuscore.restfulapi.web.DomainException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only JPA workflow for draft, independent review, publish and archive. */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/admin/thesis/assistant/knowledge")
public class ThesisAssistantKnowledgeAdminController {

    private final AssistantKnowledgeJpaWriter writer;
    private final AssistantKnowledgeDocumentJpaRepository documents;
    private final AssistantKnowledgeRevisionJpaRepository revisions;

    public ThesisAssistantKnowledgeAdminController(
            AssistantKnowledgeJpaWriter writer,
            AssistantKnowledgeDocumentJpaRepository documents,
            AssistantKnowledgeRevisionJpaRepository revisions) {
        this.writer = writer;
        this.documents = documents;
        this.revisions = revisions;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<KnowledgeDocumentView> list(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String state) {
        if (domain != null
                && !domain.isBlank()
                && !"THESIS".equalsIgnoreCase(domain)
                && !"ACADEMIC".equalsIgnoreCase(domain)) {
            return List.of();
        }
        String requestedState = state == null || state.isBlank()
                ? null
                : state.trim().toUpperCase(java.util.Locale.ROOT);
        return documents.findAll(Sort.by(Sort.Direction.ASC, "slug")).stream()
                .map(this::view)
                .filter(item -> requestedState == null || requestedState.equals(item.state()))
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeDocumentView get(@PathVariable UUID id) {
        return documents.findById(id)
                .map(this::view)
                .orElseThrow(() -> notFound(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeRevision create(
            @RequestBody KnowledgeRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return translate(() -> revision(writer.create(command(request), requireActor(actor))));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeRevision update(
            @PathVariable UUID id,
            @RequestBody KnowledgeRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return translate(() -> revision(writer.update(id, command(request), requireActor(actor))));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeRevision submit(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        return translate(() -> revision(writer.submit(id, requireActor(actor))));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeRevision publish(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        return translate(() -> revision(writer.publish(id, requireActor(actor))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        translate(() -> {
            writer.archive(id, requireActor(actor));
            return null;
        });
    }

    private KnowledgeDocumentView view(AssistantKnowledgeDocumentEntity document) {
        AssistantKnowledgeRevisionEntity revision =
                revisions.findFirstByDocumentIdOrderByVersionDesc(document.getId()).orElse(null);
        String state = !document.isActive()
                ? "ARCHIVED"
                : revision == null ? "UNVERSIONED" : revision.getState();
        return new KnowledgeDocumentView(
                document.getId(),
                revision == null ? null : revision.getId(),
                revision == null ? 0 : revision.getVersion(),
                state,
                revision == null ? document.getLocale() : revision.getLocale(),
                revision == null ? document.getSlug() : revision.getSlug(),
                revision == null ? document.getTitle() : revision.getTitle(),
                revision == null ? document.getContent() : revision.getContent(),
                revision == null ? document.getSource() : revision.getSource(),
                revision == null ? document.getPriority() : revision.getPriority(),
                revision == null ? null : revision.getCreatedBy(),
                revision == null ? null : revision.getReviewedBy(),
                revision == null ? document.getCreatedAt() : revision.getCreatedAt(),
                revision == null ? null : revision.getPublishedAt());
    }

    private static KnowledgeCommand command(KnowledgeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("knowledge request is required");
        }
        return new KnowledgeCommand(
                request.slug(),
                request.locale(),
                request.title(),
                request.content(),
                request.source(),
                request.priority(),
                null);
    }

    private static KnowledgeRevision revision(RevisionSnapshot snapshot) {
        return new KnowledgeRevision(
                snapshot.documentId(),
                snapshot.revisionId(),
                snapshot.version(),
                snapshot.state());
    }

    private static String requireActor(Jwt actor) {
        String subject = actor == null ? null : actor.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new DomainException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHENTICATED",
                    "Authentication is required");
        }
        return subject.trim();
    }

    private static <T> T translate(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (KnowledgePersistenceException exception) {
            throw switch (exception.code()) {
                case "KNOWLEDGE_NOT_FOUND" ->
                    new DomainException(HttpStatus.NOT_FOUND, exception.code(), "Knowledge document not found");
                case "KNOWLEDGE_PRIVACY_REJECTED" ->
                    new DomainException(
                            HttpStatus.BAD_REQUEST,
                            exception.code(),
                            "Knowledge content contains a prohibited personal or unsafe pattern");
                case "UNAUTHENTICATED" ->
                    new DomainException(HttpStatus.UNAUTHORIZED, exception.code(), "Authentication is required");
                default ->
                    new DomainException(
                            HttpStatus.CONFLICT,
                            exception.code(),
                            "Knowledge workflow state changed; reload and retry");
            };
        }
    }

    private static DomainException notFound(UUID id) {
        return new DomainException(
                HttpStatus.NOT_FOUND,
                "KNOWLEDGE_NOT_FOUND",
                "Knowledge document not found: " + id);
    }

    public record KnowledgeRequest(
            String slug,
            String locale,
            String title,
            String content,
            String source,
            Integer priority) {
    }

    public record KnowledgeRevision(UUID documentId, UUID revisionId, int version, String state) {
    }

    public record KnowledgeDocumentView(
            UUID documentId,
            UUID revisionId,
            int version,
            String state,
            String locale,
            String slug,
            String title,
            String content,
            String source,
            int priority,
            String createdBy,
            String reviewedBy,
            Instant createdAt,
            Instant publishedAt) {
    }
}
