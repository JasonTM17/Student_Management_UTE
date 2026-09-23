package io.campuscore.restfulapi.auth.web;

import io.campuscore.restfulapi.auth.service.AdminUserMutationService;
import io.campuscore.restfulapi.auth.web.AdminUserRequestDtos.AdminUserCreateRequest;
import io.campuscore.restfulapi.auth.web.AdminUserRequestDtos.AdminUserUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("persistence")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequestMapping("/api/v1/users")
@Tag(name = "User Management (Admin)", description = "Quản trị danh sách người dùng, cấp phát tài khoản học vụ, đặt lại mật khẩu và phân quyền hệ thống")
public class AdminUserMutationController {

    private final AdminUserMutationService users;

    public AdminUserMutationController(AdminUserMutationService users) {
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "Danh sách người dùng", description = "Truy xuất danh sách người dùng theo phân trang, lọc theo trạng thái, lọc theo vai trò hệ thống và tìm kiếm họ tên hoặc email.")
    @ApiResponse(responseCode = "200", description = "Danh sách người dùng và siêu dữ liệu phân trang")
    @ApiResponse(responseCode = "400", description = "Tham số truy vấn không được hỗ trợ hoặc vai trò lọc không hợp lệ")
    public Map<String, Object> list(
            @Parameter(description = "Số thứ tự trang (bắt đầu từ 1)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng bản ghi trên một trang") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Bộ lọc trạng thái (ACTIVE, INACTIVE, LOCKED)") @RequestParam(required = false) String status,
            @Parameter(description = "Từ khóa tìm kiếm theo tên hoặc email") @RequestParam(required = false) String search,
            @Parameter(description = "Bộ lọc vai trò hệ thống (STUDENT, LECTURER, ADMIN, TRUONG_KHOA, SUPER_ADMIN)") @RequestParam(required = false) String role,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit", "status", "search", "role"));
        return users.list(page, limit, status, search, role);
    }

    @PostMapping
    @Operation(summary = "Tạo tài khoản người dùng mới", description = "Phòng Đào tạo / Quản trị viên cấp tài khoản học vụ cho Sinh viên hoặc Giảng viên, hệ thống cấp phát mật khẩu tạm thời.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tạo tài khoản thành công, trả về thông tin người dùng và mật khẩu tạm thời"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ hoặc email đã tồn tại")
    })
    public Map<String, Object> create(
            @Valid @RequestBody AdminUserCreateRequest request,
            Authentication authentication) {
        return users.create(request.toInput(), isSuperAdmin(authentication), actorId(authentication));
    }

    @PostMapping("/{id}/password-reset")
    @Operation(summary = "Đặt lại mật khẩu người dùng", description = "Phòng Đào tạo / Quản trị viên cấp lại mật khẩu tạm thời ngẫu nhiên an toàn cho tài khoản người dùng.")
    @ApiResponse(responseCode = "200", description = "Phát hành mật khẩu mới thành công, yêu cầu đổi mật khẩu khi đăng nhập lần đầu")
    public Map<String, Object> resetPassword(
            @Parameter(description = "Mã định danh người dùng (User ID)") @PathVariable String id,
            Authentication authentication) {
        String currentUserId = authentication != null ? authentication.getName() : null;
        return users.resetPassword(id, isSuperAdmin(authentication), currentUserId);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật tài khoản người dùng", description = "Chỉnh sửa thông tin hồ sơ họ tên, quyền hạn vai trò hoặc trạng thái tài khoản.")
    @ApiResponse(responseCode = "200", description = "Cập nhật thông tin người dùng thành công")
    public Map<String, Object> update(
            @Parameter(description = "Mã định danh người dùng (User ID)") @PathVariable String id,
            @Valid @RequestBody AdminUserUpdateRequest request,
            Authentication authentication) {
        String currentUserId = authentication != null ? authentication.getName() : null;
        return users.update(id, request.toInput(), isSuperAdmin(authentication), currentUserId);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa tài khoản người dùng", description = "Xóa người dùng khỏi hệ thống đào tạo kèm kiểm tra bảo vệ tài khoản siêu quản trị viên.")
    @ApiResponse(responseCode = "200", description = "Xóa tài khoản thành công")
    public Map<String, String> delete(
            @Parameter(description = "Mã định danh người dùng (User ID)") @PathVariable String id,
            Authentication authentication) {
        String currentUserId = authentication != null ? authentication.getName() : null;
        users.delete(id, isSuperAdmin(authentication), currentUserId);
        return Map.of("message", "User deleted successfully");
    }

    private static boolean isSuperAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
    }

    /** The JWT subject, used as the actor on audit rows. */
    private static String actorId(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }

    /**
     * Rejects parameters the list contract does not implement, so a filter that
     * looks applied (the role tabs did) fails loudly instead of silently
     * returning an unfiltered page. Same contract as the catalog read routes,
     * including the transport-level cache-buster exemption.
     */
    private static void requireAllowedQuery(
            MultiValueMap<String, String> queryParameters,
            Set<String> allowed) {
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            if ("_cc_nocache".equals(entry.getKey())) {
                continue;
            }
            if (!allowed.contains(entry.getKey()) || entry.getValue().size() != 1) {
                throw new IllegalArgumentException("Unexpected or repeated query parameter: " + entry.getKey());
            }
        }
    }
}
