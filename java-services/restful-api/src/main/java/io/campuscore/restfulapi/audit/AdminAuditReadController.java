package io.campuscore.restfulapi.audit;

import io.campuscore.restfulapi.web.DomainException;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Round-3 contract ct-2: the governance read side for the admin audit trail.
 * ADMIN and SUPER_ADMIN only; lecturers and students never see it. Filters are
 * exact-match and the page shape follows the {data, meta} contract the other
 * admin lists use.
 */
@Tag(name = "Admin Audit", description = "Vết kiểm toán các thao tác quản trị: đổi vai trò, đặt lại mật khẩu, xóa dữ liệu, gỡ khóa tài khoản")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminAuditReadController {

    private final AdminAuditReadRepository audits;

    public AdminAuditReadController(AdminAuditReadRepository audits) {
        this.audits = audits;
    }

    @Operation(summary = "Danh sách vết kiểm toán quản trị", description = "Truy vấn phân trang các bản ghi audit hệ thống, lọc theo hành động, loại/thực thể hoặc tác nhân.")
    @ApiResponse(responseCode = "200", description = "Trang bản ghi audit kèm siêu dữ liệu phân trang")
    @GetMapping("/audit")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String actorId,
            @RequestParam Map<String, String> queryParameters) {
        Set<String> allowed = Set.of("page", "limit", "action", "entityType", "entityId", "actorId");
        for (String key : queryParameters.keySet()) {
            if (!allowed.contains(key) && !"cacheBuster".equals(key)) {
                throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                        "Unsupported query parameter: " + key);
            }
        }
        if (page < 1 || limit < 1 || limit > AdminAuditReadRepository.MAX_PAGE_SIZE) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "limit must be between 1 and " + AdminAuditReadRepository.MAX_PAGE_SIZE);
        }
        AdminAuditReadRepository.AuditPage result =
                audits.page(page, limit, action, entityType, entityId, actorId);
        Map<String, Object> meta = Map.of(
                "total", result.total(),
                "page", result.page(),
                "limit", result.limit(),
                "totalPages", result.totalPages());
        return Map.of("data", result.data(), "meta", meta);
    }
}
