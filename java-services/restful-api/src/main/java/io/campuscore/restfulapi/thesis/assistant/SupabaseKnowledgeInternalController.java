package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantKnowledgeAdminController.KnowledgeDocumentView;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantKnowledgeAdminController.KnowledgeRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantKnowledgeAdminController.KnowledgeRevision;
import io.campuscore.restfulapi.web.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Private RAG-side authority boundary.  Never expose this route through Caddy. */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "assistant.knowledge", name = "authority-mode", havingValue = "supabase")
@RequestMapping({"/internal/rag/assistant/knowledge", "/internal/rag/thesis/assistant/knowledge"})
public class SupabaseKnowledgeInternalController {
    private static final String TOKEN_HEADER = "X-Rag-Service-Token";

    private final SupabaseKnowledgeAuthorityService authority;
    private final SupabaseKnowledgeSyncService sync;
    private final AssistantRagProperties ragProperties;

    public SupabaseKnowledgeInternalController(SupabaseKnowledgeAuthorityService authority,
            SupabaseKnowledgeSyncService sync, AssistantRagProperties ragProperties) {
        this.authority = authority;
        this.sync = sync;
        this.ragProperties = ragProperties;
    }

    @GetMapping
    public List<KnowledgeDocumentView> list(@RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String actor,
            @RequestParam(required = false) String domain, @RequestParam(required = false) String state) {
        verify(token);
        return authority.list(owner(actor), domain, state);
    }

    @GetMapping("/{id}")
    public KnowledgeDocumentView get(@PathVariable UUID id,
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String actor) {
        verify(token);
        return authority.get(id, owner(actor));
    }

    @PostMapping
    public KnowledgeRevision create(@RequestBody KnowledgeRequest request,
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String actor) {
        verify(token);
        ThesisAssistantKnowledgeAdminController.validate(request);
        return authority.create(request, owner(actor));
    }

    @PutMapping("/{id}")
    public KnowledgeRevision update(@PathVariable UUID id, @RequestBody KnowledgeRequest request,
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String actor) {
        verify(token);
        ThesisAssistantKnowledgeAdminController.validate(request);
        return authority.update(id, request, owner(actor));
    }

    @PostMapping("/{id}/submit")
    public KnowledgeRevision submit(@PathVariable UUID id,
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String actor) {
        verify(token);
        return authority.submit(id, owner(actor));
    }

    @PostMapping("/{id}/publish")
    public KnowledgeRevision publish(@PathVariable UUID id,
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String actor) {
        verify(token);
        String owner = owner(actor);
        KnowledgeRevision published = authority.publish(id, owner);
        SupabaseKnowledgeSyncService.SyncResult projection = sync.syncNow();
        return new KnowledgeRevision(published.documentId(), published.revisionId(), published.version(), published.state(),
                published.releaseId(), published.corpusVersion(), published.corpusHash(), published.rowCount(), projection);
    }

    @DeleteMapping("/{id}")
    public void archive(@PathVariable UUID id,
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String actor) {
        verify(token);
        authority.archive(id, owner(actor));
        // A later release publication will carry the tombstone.  Do not claim
        // that the old active projection changed when Supabase has no new
        // published release yet.
    }

    @PostMapping("/sync")
    public SupabaseKnowledgeSyncService.SyncResult sync(
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String actor) {
        verify(token);
        owner(actor);
        return sync.syncNow();
    }

    @GetMapping("/sync-status")
    public SupabaseKnowledgeSyncService.SyncResult status(
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String actor) {
        verify(token);
        owner(actor);
        return sync.status();
    }

    private void verify(String token) {
        if (!ragProperties.serviceMode() || !ragProperties.tokenConfigured()) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "RAG_SERVICE_DISABLED", "RAG service is not configured");
        }
        if (token == null || token.isBlank()) {
            throw new DomainException(HttpStatus.FORBIDDEN, "RAG_SERVICE_UNAUTHORIZED", "RAG service token is required");
        }
        byte[] expected = ragProperties.serviceToken().getBytes(StandardCharsets.UTF_8);
        byte[] presented = token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, presented)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "RAG_SERVICE_UNAUTHORIZED", "RAG service token is invalid");
        }
    }

    private static String owner(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Assistant owner is required");
        }
        return actor;
    }
}
