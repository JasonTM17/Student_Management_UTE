package io.campuscore.restfulapi.thesis.assistant.controller;

import io.campuscore.restfulapi.thesis.assistant.dto.KnowledgeDocumentView;
import io.campuscore.restfulapi.thesis.assistant.dto.KnowledgeRequest;
import io.campuscore.restfulapi.thesis.assistant.dto.KnowledgeRevision;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
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
import io.campuscore.restfulapi.thesis.assistant.service.AssistantKnowledgeAdminService;

/** Admin-only CRUD and Draft -> review -> publish workflow for public campus knowledge. */
@Tag(name = "AI Assistant Knowledge Base (Admin)", description = "Quản trị tài liệu tri thức RAG: quy chế, hướng dẫn học vụ, quy trình kiểm duyệt hai bước (Draft -> Review -> Publish)")
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "assistant.knowledge", name = "authority-mode", havingValue = "sql", matchIfMissing = true)
@RequestMapping({"/api/v1/admin/assistant/knowledge", "/api/v1/admin/thesis/assistant/knowledge"})
public class ThesisAssistantKnowledgeAdminController {
    private final AssistantKnowledgeAdminService service;

    public ThesisAssistantKnowledgeAdminController(AssistantKnowledgeAdminService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách tài liệu tri thức RAG", description = "Truy vấn danh sách tài liệu tri thức học vụ theo lĩnh vực (domain) và trạng thái (DRAFT, PENDING_REVIEW, PUBLISHED, ARCHIVED)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy vấn thành công")
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<KnowledgeDocumentView> list(
            @Parameter(description = "Lĩnh vực tri thức (THESIS, ACADEMIC, ENROLLMENT, CONDUCT, TUITION)") @RequestParam(required = false) String domain,
            @Parameter(description = "Trạng thái phê duyệt (DRAFT, PENDING_REVIEW, PUBLISHED, ARCHIVED)") @RequestParam(required = false) String state) {
        return service.list(domain, state);
    }

    @Operation(summary = "Xem chi tiết tài liệu tri thức", description = "Lấy nội dung tài liệu và phiên bản mới nhất theo ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tìm thấy tài liệu"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy tài liệu")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeDocumentView get(
            @Parameter(description = "Mã định danh tài liệu tri thức (UUID)", required = true) @PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Tạo mới tài liệu tri thức (Bản nháp DRAFT)", description = "Tạo một tài liệu tri thức mới kèm phiên bản 1 trạng thái DRAFT")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tạo tài liệu thành công")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeRevision create(@RequestBody KnowledgeRequest request, @AuthenticationPrincipal Jwt actor) {
        return service.create(request, actor);
    }

    @Operation(summary = "Cập nhật bản nháp tài liệu tri thức", description = "Chỉnh sửa nội dung tài liệu bản nháp của người tạo hoặc sinh phiên bản mới")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cập nhật thành công")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeRevision update(
            @Parameter(description = "Mã định danh tài liệu tri thức (UUID)", required = true) @PathVariable UUID id,
            @RequestBody KnowledgeRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return service.update(id, request, actor);
    }

    @Operation(summary = "Nộp bản nháp để chờ xét duyệt (Submit for Review)", description = "Chuyển trạng thái phiên bản từ DRAFT sang PENDING_REVIEW để người khác duyệt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Nộp duyệt thành công"),
        @ApiResponse(responseCode = "409", description = "Xung đột trạng thái phiên bản")
    })
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeRevision submit(
            @Parameter(description = "Mã định danh tài liệu tri thức (UUID)", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return service.submit(id, actor);
    }

    @Operation(summary = "Phê duyệt và xuất bản tài liệu tri thức (Publish - 2-man rule)", description = "Yêu cầu một Admin khác với người tạo kiểm duyệt và chính thức xuất bản tri thức cho AI sử dụng")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Xuất bản thành công"),
        @ApiResponse(responseCode = "409", description = "Người tạo không thể tự duyệt phiên bản của chính mình")
    })
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KnowledgeRevision publish(
            @Parameter(description = "Mã định danh tài liệu tri thức (UUID)", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return service.publish(id, actor);
    }

    @Operation(summary = "Lưu trữ tài liệu tri thức (Archive/Delete)", description = "Đưa tài liệu tri thức vào trạng thái lưu trữ ngưng hoạt động")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lưu trữ thành công"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy tài liệu")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public void delete(
            @Parameter(description = "Mã định danh tài liệu tri thức (UUID)", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        service.delete(id, actor);
    }
}
