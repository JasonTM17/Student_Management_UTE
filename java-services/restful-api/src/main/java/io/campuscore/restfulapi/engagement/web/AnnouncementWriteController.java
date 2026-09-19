package io.campuscore.restfulapi.engagement.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.campuscore.restfulapi.engagement.service.AnnouncementWriteService;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementHistoryListResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.CreateAnnouncementRequest;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.DeleteAnnouncementResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.LifecycleRequest;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.UpdateAnnouncementRequest;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.UpdateDisplayOrderRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.oauth2.jwt.Jwt;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Role-protected announcement mutation and governance routes. */
@Tag(name = "Announcements & Campus News", description = "Truy vấn thông báo học vụ, bản tin trường HCM-UTE, quản lý vòng đời bài viết (soạn thảo, cập nhật, lưu trữ, lịch sử chỉnh sửa)")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/announcements")
public class AnnouncementWriteController {

    private final AnnouncementWriteService announcements;

    public AnnouncementWriteController(AnnouncementWriteService announcements) {
        this.announcements = announcements;
    }

    @Operation(summary = "Tạo thông báo hoặc tin tức mới", description = "Tạo bài thông báo mới kèm phân quyền phạm vi đối tượng (toàn trường, khoa, bộ môn, lớp học phần)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Tạo thông báo thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu thông báo không hợp lệ"),
        @ApiResponse(responseCode = "403", description = "Không có quyền tạo thông báo")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('LECTURER', 'ADMIN', 'SUPER_ADMIN')")
    public AnnouncementResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JsonNode request) {
        return announcements.create(subject(jwt), actorLabel(jwt), jwt.getClaimAsStringList("roles"),
                jwt.getClaimAsString("lecturerId"), CreateAnnouncementRequest.from(request));
    }

    @Operation(summary = "Cập nhật nội dung thông báo", description = "Chỉnh sửa tiêu đề, nội dung, phạm vi hoặc tệp đính kèm của thông báo hiện có")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy thông báo")
    })
    @PutMapping("{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('LECTURER', 'ADMIN', 'SUPER_ADMIN')")
    public AnnouncementResponse update(
            @Parameter(description = "Mã định danh thông báo (UUID)", required = true) @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JsonNode request) {
        return announcements.update(subject(jwt), actorLabel(jwt), jwt.getClaimAsStringList("roles"),
                jwt.getClaimAsString("lecturerId"), id, UpdateAnnouncementRequest.from(request));
    }

    @Operation(summary = "Xem lịch sử thay đổi phiên bản thông báo", description = "Truy xuất danh sách các phiên bản đã chỉnh sửa và kiểm toán vết kiểm toán của thông báo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy lịch sử thành công")
    })
    @GetMapping("{id}/history")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('LECTURER', 'ADMIN', 'SUPER_ADMIN')")
    public AnnouncementHistoryListResponse history(
            @Parameter(description = "Mã định danh thông báo (UUID)", required = true) @PathVariable String id,
            @Parameter(description = "Số trang phân trang") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang") @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters,
            @AuthenticationPrincipal Jwt jwt) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        subject(jwt);
        return announcements.history(id, page, limit);
    }

    @Operation(summary = "Đặt thứ tự hiển thị của thông báo", description = "Quản trị viên gán thứ tự hiển thị cho trang chủ; số nhỏ hơn hiển thị trước, thông báo không có thứ tự nằm cuối danh sách")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cập nhật thứ tự hiển thị thành công"),
        @ApiResponse(responseCode = "400", description = "displayOrder không hợp lệ"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy thông báo")
    })
    @PatchMapping("{id}/order")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AnnouncementResponse updateDisplayOrder(
            @Parameter(description = "Mã định danh thông báo (UUID)", required = true) @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateDisplayOrderRequest request) {
        return announcements.setDisplayOrder(
                subject(jwt),
                actorLabel(jwt),
                id,
                request.displayOrder());
    }

    @Operation(summary = "Lưu trữ (Archive) thông báo", description = "Đưa thông báo vào trạng thái lưu trữ bảo mật")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lưu trữ thông báo thành công")
    })
    @PostMapping("{id}/archive")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AnnouncementResponse archive(
            @Parameter(description = "Mã định danh thông báo (UUID)", required = true) @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JsonNode request) {
        return announcements.archive(subject(jwt), actorLabel(jwt), id, LifecycleRequest.from(request));
    }

    @Operation(summary = "Khôi phục thông báo từ lưu trữ", description = "Khôi phục trạng thái hoạt động bình thường của thông báo đã lưu trữ")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Khôi phục thành công")
    })
    @PostMapping("{id}/restore")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AnnouncementResponse restore(
            @Parameter(description = "Mã định danh thông báo (UUID)", required = true) @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JsonNode request) {
        return announcements.restore(subject(jwt), actorLabel(jwt), id, LifecycleRequest.from(request));
    }

    /**
     * Kept for clients that still call DELETE. It now archives safely and leaves
     * the audit trail intact instead of removing the row.
     */
    @Operation(summary = "Xóa thông báo (Soft Delete an toàn)", description = "Lưu trữ an toàn thông báo và bảo toàn vết kiểm toán audit log")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Xóa thành công")
    })
    @DeleteMapping("{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public DeleteAnnouncementResponse delete(
            @Parameter(description = "Mã định danh thông báo (UUID)", required = true) @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        return announcements.delete(subject(jwt), actorLabel(jwt), id);
    }

    private static void requireAllowedQuery(
            MultiValueMap<String, String> queryParameters,
            Set<String> allowed) {
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            if ("_cc_nocache".equals(entry.getKey())) {
                continue;
            }
            if (!allowed.contains(entry.getKey()) || entry.getValue().size() != 1) {
                throw new IllegalArgumentException(
                        "Unexpected or repeated query parameter: " + entry.getKey());
            }
        }
    }

    private static String subject(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new AccessDeniedException("Not authenticated");
        }
        return jwt.getSubject();
    }

    /**
     * Keep the immutable actor id for authorization/audit joins while exposing a
     * useful human label in the Admin history UI. Claims are optional because
     * older tokens only carry a subject and email.
     */
    private static String actorLabel(Jwt jwt) {
        String firstName = claimAsString(jwt, "firstName");
        String lastName = claimAsString(jwt, "lastName");
        String fullName = (firstName + " " + lastName).trim();
        String email = claimAsString(jwt, "email");
        if (!fullName.isBlank()) {
            return email.isBlank() ? fullName : fullName + " · " + email;
        }
        return email.isBlank() ? subject(jwt) : email;
    }

    private static String claimAsString(Jwt jwt, String claim) {
        if (jwt == null) {
            return "";
        }
        Object value = jwt.getClaims().get(claim);
        return value == null ? "" : value.toString().trim();
    }
}
