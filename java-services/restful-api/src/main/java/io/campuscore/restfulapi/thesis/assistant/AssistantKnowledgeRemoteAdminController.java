package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantKnowledgeAdminController.KnowledgeDocumentView;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantKnowledgeAdminController.KnowledgeRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantKnowledgeAdminController.KnowledgeRevision;
import io.campuscore.restfulapi.web.DomainException;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
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

/**
 * Production REST edge for knowledge governance.  Supabase credentials stay
 * inside the private RAG service; this controller forwards authenticated admin
 * operations over the internal token boundary.
 */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "assistant.knowledge", name = "authority-mode", havingValue = "remote")
@RequestMapping({"/api/v1/admin/assistant/knowledge", "/api/v1/admin/thesis/assistant/knowledge"})
public class AssistantKnowledgeRemoteAdminController {
    private final AssistantKnowledgeAuthorityGateway authority;

    public AssistantKnowledgeRemoteAdminController(AssistantKnowledgeAuthorityGateway authority) {
        this.authority = authority;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<KnowledgeDocumentView> list(@RequestParam(required = false) String domain,
            @RequestParam(required = false) String state, @AuthenticationPrincipal Jwt actor) {
        return authority.list(subject(actor), domain, state);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeDocumentView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        return authority.get(id, subject(actor));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeRevision create(@RequestBody KnowledgeRequest request, @AuthenticationPrincipal Jwt actor) {
        ThesisAssistantKnowledgeAdminController.validate(request);
        return authority.create(request, subject(actor));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeRevision update(@PathVariable UUID id, @RequestBody KnowledgeRequest request,
            @AuthenticationPrincipal Jwt actor) {
        ThesisAssistantKnowledgeAdminController.validate(request);
        return authority.update(id, request, subject(actor));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeRevision submit(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        return authority.submit(id, subject(actor));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeRevision publish(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        return authority.publish(id, subject(actor));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public void archive(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        authority.archive(id, subject(actor));
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public SupabaseKnowledgeSyncService.SyncResult sync(@AuthenticationPrincipal Jwt actor) {
        return authority.sync(subject(actor));
    }

    @GetMapping("/sync-status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public SupabaseKnowledgeSyncService.SyncResult status(@AuthenticationPrincipal Jwt actor) {
        return authority.status(subject(actor));
    }

    private static String subject(Jwt actor) {
        String subject = actor == null ? null : actor.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        return subject;
    }
}
