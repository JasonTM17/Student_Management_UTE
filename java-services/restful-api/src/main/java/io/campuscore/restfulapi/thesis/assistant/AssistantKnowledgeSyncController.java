package io.campuscore.restfulapi.thesis.assistant;

import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Administrative boundary for Supabase -> PostgreSQL release promotion. */
@Tag(name = "AI Assistant Knowledge Sync (Admin)", description = "Đồng bộ hóa dữ liệu tri thức giữa Supabase và PostgreSQL")
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "assistant.knowledge", name = "authority-mode", havingValue = "sql", matchIfMissing = true)
@RequestMapping({"/api/v1/admin/assistant/knowledge", "/api/v1/admin/thesis/assistant/knowledge"})
public class AssistantKnowledgeSyncController {
    private final SupabaseKnowledgeSyncService sync;

    public AssistantKnowledgeSyncController(SupabaseKnowledgeSyncService sync) {
        this.sync = sync;
    }

    @Operation(summary = "Thực hiện đồng bộ tri thức ngay lập tức (Sync Now)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Đồng bộ thành công")
    })
    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public SupabaseKnowledgeSyncService.SyncResult sync() {
        return sync.syncNow();
    }

    @Operation(summary = "Kiểm tra trạng thái đồng bộ tri thức")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy trạng thái thành công")
    })
    @GetMapping("/sync-status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public SupabaseKnowledgeSyncService.SyncResult status() {
        return sync.status();
    }
}
