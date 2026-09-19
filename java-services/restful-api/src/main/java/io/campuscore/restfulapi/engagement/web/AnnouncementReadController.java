package io.campuscore.restfulapi.engagement.web;

import io.campuscore.restfulapi.engagement.service.AnnouncementReadService;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementListResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.PublicAnnouncementListResponse;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Role-aware announcement query routes for the course portal. */
@Tag(name = "Announcements & Campus News", description = "Truy vấn thông báo học vụ, bản tin trường HCM-UTE, quản lý vòng đời bài viết (soạn thảo, cập nhật, lưu trữ, lịch sử chỉnh sửa)")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/announcements")
public class AnnouncementReadController {

    private final AnnouncementReadService announcements;

    public AnnouncementReadController(AnnouncementReadService announcements) {
        this.announcements = announcements;
    }

    /**
     * Anonymous campus news feed for the public homepage. Returns only
     * PUBLISHED, globally visible, unexpired announcements — no identity is
     * required and no audience-scoping metadata is exposed.
     */
    @Operation(summary = "Bản tin công khai cho khách truy cập (không cần đăng nhập)", description = "Danh sách thông báo đã xuất bản, công khai toàn trường và còn trong thời hạn hiển thị, dùng cho trang chủ")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy vấn bản tin công khai thành công")
    })
    @GetMapping("public")
    public PublicAnnouncementListResponse getPublicAnnouncements(
            @Parameter(description = "Số trang phân trang (bắt đầu từ 1)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng thông báo trên một trang") @RequestParam(defaultValue = "9") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return announcements.findPublic(page, limit);
    }

    @Operation(summary = "Lấy danh sách thông báo dành riêng cho người dùng", description = "Truy vấn thông báo phân quyền theo vai trò (Sinh viên, Giảng viên, Quản trị viên) và đối tượng lớp/học kỳ")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy vấn danh sách thông báo thành công"),
        @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính JWT"),
        @ApiResponse(responseCode = "403", description = "Thiếu thông tin phân quyền hợp lệ")
    })
    @GetMapping("my")
    public AnnouncementListResponse getMyAnnouncements(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Số trang phân trang (bắt đầu từ 1)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng thông báo trên một trang") @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        List<String> roles = requireIdentity(jwt);
        String studentId = stringClaim(jwt, "studentId");
        Integer studentYear = studentYear(jwt);
        String lecturerId = stringClaim(jwt, "lecturerId");
        requireProfileClaims(roles, studentId, studentYear, lecturerId);
        return announcements.findForUser(
                roles,
                studentId,
                studentYear,
                lecturerId,
                page,
                limit);
    }

    @Operation(summary = "Quản trị tra cứu toàn bộ thông báo (Admin/SuperAdmin)", description = "Truy vấn danh sách tất cả thông báo hệ thống với các bộ lọc học kỳ, lớp học phần, mức độ ưu tiên và trạng thái")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy danh sách thông báo thành công"),
        @ApiResponse(responseCode = "403", description = "Chỉ Quản trị viên mới có quyền truy cập")
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AnnouncementListResponse getAllAnnouncements(
            @Parameter(description = "Số trang phân trang (bắt đầu từ 1)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng thông báo trên một trang") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Mã định danh học kỳ (UUID)") @RequestParam(required = false) String semesterId,
            @Parameter(description = "Mã định danh lớp học phần (UUID)") @RequestParam(required = false) String sectionId,
            @Parameter(description = "Mức độ ưu tiên (LOW, NORMAL, HIGH, URGENT)") @RequestParam(required = false) String priority,
            @Parameter(description = "Trạng thái thông báo (ACTIVE, ARCHIVED)") @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam MultiValueMap<String, String> queryParameters,
            @AuthenticationPrincipal Jwt jwt) {
        requireAllowedQuery(
                queryParameters,
                Set.of("page", "limit", "semesterId", "sectionId", "priority", "status"));
        requireIdentity(jwt);
        return announcements.findAll(
                page,
                limit,
                normalizeOptional(semesterId),
                normalizeOptional(sectionId),
                priority,
                status);
    }

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

    private static List<String> requireIdentity(Jwt jwt) {
        String subject = stringClaim(jwt, "sub");
        if (subject == null
                || subject.isBlank()
                || stringClaim(jwt, "email") == null) {
            throw new BadCredentialsException("Invalid JWT claims");
        }
        return values(jwt, "roles");
    }

    private static void requireProfileClaims(
            List<String> roles,
            String studentId,
            Integer studentYear,
            String lecturerId) {
        if (roles.contains("STUDENT") && (studentId == null || studentYear == null)) {
            throw new AccessDeniedException("Student profile claims are required");
        }
        if (roles.contains("LECTURER") && lecturerId == null) {
            throw new AccessDeniedException("Lecturer profile claim is required");
        }
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static List<String> values(Jwt jwt, String claimName) {
        if (jwt == null) {
            return List.of();
        }
        Object claim = jwt.getClaims().get(claimName);
        if (claim == null) {
            return List.of();
        }
        if (!(claim instanceof Collection<?> values)) {
            throw new BadCredentialsException("Invalid " + claimName + " claim");
        }
        return values.stream()
                .map(value -> {
                    if (!(value instanceof String text) || text.isBlank()) {
                        throw new BadCredentialsException("Invalid " + claimName + " claim");
                    }
                    return text;
                })
                .toList();
    }

    private static String stringClaim(Jwt jwt, String claimName) {
        if (jwt == null) {
            return null;
        }
        Object value = jwt.getClaims().get(claimName);
        if (!(value instanceof String text)) {
            return null;
        }
        return text.isEmpty() ? null : text;
    }

    private static Integer studentYear(Jwt jwt) {
        if (jwt == null || !(jwt.getClaims().get("student") instanceof Map<?, ?> student)) {
            return null;
        }
        Object year = student.get("year");
        if (year instanceof Byte || year instanceof Short || year instanceof Integer) {
            return ((Number) year).intValue();
        }
        if (year instanceof Long longYear
                && longYear >= Integer.MIN_VALUE
                && longYear <= Integer.MAX_VALUE) {
            return longYear.intValue();
        }
        if (year instanceof BigInteger bigYear
                && bigYear.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                && bigYear.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
            return bigYear.intValue();
        }
        return null;
    }
}
