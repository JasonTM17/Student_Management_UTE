package io.campuscore.restfulapi.thesis.assistant;

import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrative boundary for Supabase -> PostgreSQL release promotion. */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "assistant.knowledge", name = "authority-mode", havingValue = "sql", matchIfMissing = true)
@RequestMapping({"/api/v1/admin/assistant/knowledge", "/api/v1/admin/thesis/assistant/knowledge"})
public class AssistantKnowledgeSyncController {
    private final SupabaseKnowledgeSyncService sync;

    public AssistantKnowledgeSyncController(SupabaseKnowledgeSyncService sync) {
        this.sync = sync;
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public SupabaseKnowledgeSyncService.SyncResult sync() {
        return sync.syncNow();
    }

    @GetMapping("/sync-status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public SupabaseKnowledgeSyncService.SyncResult status() {
        return sync.status();
    }
}
