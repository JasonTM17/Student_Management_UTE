package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicSectionReadService;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.LecturerGradingSectionResponse;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.LecturerScheduleResponse;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.SectionGradesResponse;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.SectionListResponse;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.SectionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Section, roster and lecturer grading query routes. */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/sections")
@Tag(name = "Academic Sections & Grading", description = "Quản lý và tra cứu lớp học phần, thời khóa biểu giảng dạy và bảng điểm sinh viên theo lớp")
public class AcademicSectionReadController {

    private final AcademicSectionReadService academic;

    public AcademicSectionReadController(AcademicSectionReadService academic) {
        this.academic = academic;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách lớp học phần", description = "Tra cứu danh sách các lớp học phần mở trong học kỳ, lọc theo học kỳ, khoa hoặc học phần.")
    @ApiResponse(responseCode = "200", description = "Danh sách lớp học phần phân trang")
    public SectionListResponse getSections(
            @Parameter(description = "Số thứ tự trang") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang") @RequestParam(defaultValue = "100") int limit,
            @Parameter(description = "Mã học kỳ (Semester ID)") @RequestParam(required = false) String semesterId,
            @Parameter(description = "Mã Bộ môn (Department ID)") @RequestParam(required = false) String departmentId,
            @Parameter(description = "Mã học phần (Course ID)") @RequestParam(required = false) String courseId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit", "semesterId", "departmentId", "courseId"));
        return academic.findSections(page, limit, semesterId, departmentId, courseId);
    }

    @GetMapping("my/schedule")
    @PreAuthorize("hasRole('LECTURER')")
    @Operation(summary = "Thời khóa biểu giảng dạy của giảng viên", description = "Truy xuất lịch dạy theo tuần và phòng học của giảng viên đang đăng nhập.")
    @ApiResponse(responseCode = "200", description = "Danh sách lịch giảng dạy")
    public List<LecturerScheduleResponse> getMySchedule(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã học kỳ (tùy chọn)") @RequestParam(required = false) String semesterId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("semesterId"));
        return academic.findLecturerSchedule(jwt.getClaimAsString("lecturerId"), semesterId);
    }

    @GetMapping("my/grading")
    @PreAuthorize("hasRole('LECTURER')")
    @Operation(summary = "Danh sách lớp cần nhập điểm của giảng viên", description = "Lấy danh sách các lớp học phần được phân công giảng dạy kèm tiến độ nhập điểm QT và CK.")
    @ApiResponse(responseCode = "200", description = "Danh sách lớp phân công nhập điểm")
    public List<LecturerGradingSectionResponse> getMyGradingSections(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã học kỳ (tùy chọn)") @RequestParam(required = false) String semesterId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("semesterId"));
        return academic.findLecturerGradingSections(jwt.getClaimAsString("lecturerId"), semesterId);
    }

    @GetMapping("{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chi tiết lớp học phần", description = "Xem thông tin chi tiết về sĩ số, phòng học, lịch học và giảng viên phụ trách lớp học phần.")
    @ApiResponse(responseCode = "200", description = "Thông tin chi tiết lớp học phần")
    public SectionResponse getSection(@Parameter(description = "Mã lớp học phần (Section ID)") @PathVariable String id) {
        return academic.findSection(id);
    }

    @GetMapping("{id}/grades")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER')")
    @Operation(summary = "Bảng điểm lớp học phần", description = "Tra cứu bảng điểm chi tiết (điểm Quá trình 50% + Cuối kỳ 50%) của tất cả sinh viên trong lớp.")
    @ApiResponse(responseCode = "200", description = "Bảng điểm chi tiết của lớp học phần")
    public SectionGradesResponse getSectionGrades(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã lớp học phần (Section ID)") @PathVariable String id) {
        return academic.findSectionGrades(
                id,
                jwt.getClaimAsStringList("roles"),
                jwt.getClaimAsString("lecturerId"));
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
}
