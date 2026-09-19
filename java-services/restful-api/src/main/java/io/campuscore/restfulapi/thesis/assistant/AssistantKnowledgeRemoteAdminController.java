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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Production REST edge for knowledge governance.  Supabase credentials stay
 * inside the private RAG service; this controller forwards authenticated admin
 * operations over the internal token boundary.
 */
@Tag(name = "AI Assistant Knowledge Remote Gateway (Admin)", description = "Cổng kết nối quản trị kho tri thức RAG từ xa ủy quyền qua Supabase RAG microservice")
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "assistant.knowledge", name = "authority-mode", havingValue = "remote")
@RequestMapping({"/api/v1/admin/assistant/knowledge", "/api/v1/admin/thesis/assistant/knowledge"})
public class AssistantKnowledgeRemoteAdminController {
    private final AssistantKnowledgeAuthorityGateway authority;

    public AssistantKnowledgeRemoteAdminController(AssistantKnowledgeAuthorityGateway authority) {
        this.authority = authority;
    }

    @Operation(summary = "Danh sách tài liệu tri thức RAG từ xa", description = "Truy vấn danh sách tài liệu tri thức từ gateway từ xa")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy vấn thành công")
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<KnowledgeDocumentView> list(
            @Parameter(description = "Lĩnh vực tri thức") @RequestParam(required = false) String domain,
            @Parameter(description = "Trạng thái phê duyệt") @RequestParam(required = false) String state,
            @AuthenticationPrincipal Jwt actor) {
        return authority.list(subject(actor), domain, state);
    }

    @Operation(summary = "Xem chi tiết tài liệu tri thức từ xa")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tìm thấy tài liệu")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeDocumentView get(
            @Parameter(description = "Mã định danh (UUID)", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return authority.get(id, subject(actor));
    }

    @Operation(summary = "Tạo mới tài liệu tri thức từ xa (DRAFT)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tạo thành công")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeRevision create(@RequestBody KnowledgeRequest request, @AuthenticationPrincipal Jwt actor) {
        ThesisAssistantKnowledgeAdminController.validate(request);
        return authority.create(request, subject(actor));
    }

    @Operation(summary = "Cập nhật tài liệu tri thức từ xa")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cập nhật thành công")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeRevision update(
            @Parameter(description = "Mã định danh (UUID)", required = true) @PathVariable UUID id,
            @RequestBody KnowledgeRequest request,
            @AuthenticationPrincipal Jwt actor) {
        ThesisAssistantKnowledgeAdminController.validate(request);
        return authority.update(id, request, subject(actor));
    }

    @Operation(summary = "Nộp bản nháp tài liệu tri thức từ xa để duyệt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Nộp thành công")
    })
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeRevision submit(
            @Parameter(description = "Mã định danh (UUID)", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return authority.submit(id, subject(actor));
    }

    @Operation(summary = "Phê duyệt và xuất bản tài liệu tri thức từ xa")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Xuất bản thành công")
    })
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeRevision publish(
            @Parameter(description = "Mã định danh (UUID)", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return authority.publish(id, subject(actor));
    }

    @Operation(summary = "Lưu trữ tài liệu tri thức từ xa")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lưu trữ thành công")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public void archive(
            @Parameter(description = "Mã định danh (UUID)", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        authority.archive(id, subject(actor));
    }

    @Operation(summary = "Đồng bộ hóa kho tri thức từ xa")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Kích hoạt đồng bộ thành công")
    })
    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public SupabaseKnowledgeSyncService.SyncResult sync(@AuthenticationPrincipal Jwt actor) {
        return authority.sync(subject(actor));
    }

    @Operation(summary = "Kiểm tra trạng thái đồng bộ hóa kho tri thức từ xa")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy trạng thái thành công")
    })
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
