package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicAttendanceReadService;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.AttendanceListResponse;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.AttendanceResponse;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.SectionAttendanceSummaryResponse;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.StudentAttendanceSummaryResponse;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Role-protected academic attendance query routes. */
@Tag(name = "Attendance Tracking", description = "Quản lý và tra cứu chuyên cần, điểm danh sinh viên theo lớp học phần và cảnh báo vắng học")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/attendance")
public class AcademicAttendanceReadController {

    private final AcademicAttendanceReadService academic;

    public AcademicAttendanceReadController(AcademicAttendanceReadService academic) {
        this.academic = academic;
    }

    @Operation(summary = "Quản trị tra cứu toàn bộ dữ liệu điểm danh", description = "Truy vấn dữ liệu điểm danh toàn hệ thống theo lớp học phần, sinh viên hoặc ngày học")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy vấn thành công")
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AttendanceListResponse getAttendance(
            @Parameter(description = "Số trang") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Mã định danh lớp học phần (UUID)") @RequestParam(required = false) String sectionId,
            @Parameter(description = "Mã định danh sinh viên (UUID)") @RequestParam(required = false) String studentId,
            @Parameter(description = "Ngày điểm danh (YYYY-MM-DD)") @RequestParam(required = false) String date,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit", "sectionId", "studentId", "date"));
        return academic.findAll(page, limit, sectionId, studentId, date);
    }

    @Operation(summary = "Sinh viên tra cứu lịch sử điểm danh của bản thân", description = "Tra cứu các buổi có mặt, vắng hoặc có phép trong lớp học phần hoặc học kỳ")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy lịch sử điểm danh thành công")
    })
    @GetMapping("my")
    @PreAuthorize("hasRole('STUDENT')")
    public List<AttendanceResponse> getMyAttendance(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh lớp học phần (UUID)") @RequestParam(required = false) String sectionId,
            @Parameter(description = "Mã định danh học kỳ (UUID)") @RequestParam(required = false) String semesterId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("sectionId", "semesterId"));
        return academic.findStudentAttendance(jwt.getClaimAsString("studentId"), sectionId, semesterId);
    }

    @Operation(summary = "Sinh viên xem tổng kết tỷ lệ chuyên cần theo học kỳ", description = "Thống kê số buổi vắng, tỷ lệ chuyên cần và nguy cơ bị cấm thi theo quy chế UTE")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy tổng kết chuyên cần thành công")
    })
    @GetMapping("my/summary")
    @PreAuthorize("hasRole('STUDENT')")
    public List<StudentAttendanceSummaryResponse> getMyAttendanceSummary(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh học kỳ (UUID)") @RequestParam(required = false) String semesterId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("semesterId"));
        return academic.findStudentAttendanceSummary(jwt.getClaimAsString("studentId"), semesterId);
    }

    @Operation(summary = "Giảng viên tra cứu dữ liệu điểm danh lớp phụ trách", description = "Truy vấn danh sách điểm danh các lớp do giảng viên đang giảng dạy")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy vấn thành công")
    })
    @GetMapping("lecturer/my")
    @PreAuthorize("hasRole('LECTURER')")
    public List<AttendanceResponse> getMySectionAttendance(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh lớp học phần (UUID)") @RequestParam(required = false) String sectionId,
            @Parameter(description = "Ngày điểm danh (YYYY-MM-DD)") @RequestParam(required = false) String date,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("sectionId", "date"));
        return academic.findLecturerAttendance(jwt.getClaimAsString("lecturerId"), sectionId, date);
    }

    @Operation(summary = "Danh sách điểm danh sinh viên trong một lớp học phần", description = "Giảng viên phụ trách hoặc Quản trị viên tra cứu chi tiết điểm danh của lớp theo ngày")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy vấn thành công")
    })
    @GetMapping("section/{sectionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER')")
    public List<AttendanceResponse> getSectionAttendance(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh lớp học phần (UUID)", required = true) @PathVariable String sectionId,
            @Parameter(description = "Ngày điểm danh (YYYY-MM-DD)") @RequestParam(required = false) String date,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("date"));
        return academic.findSectionAttendance(
                sectionId,
                date,
                jwt.getClaimAsStringList("roles"),
                jwt.getClaimAsString("lecturerId"));
    }

    @Operation(summary = "Tổng kết tỷ lệ chuyên cần và vắng học của lớp học phần", description = "Thống kê tổng số buổi học, số lượt vắng và danh sách sinh viên vắng vượt số tiết quy định")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy tổng kết lớp thành công")
    })
    @GetMapping("section/{sectionId}/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER')")
    public SectionAttendanceSummaryResponse getSectionAttendanceSummary(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh lớp học phần (UUID)", required = true) @PathVariable String sectionId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return academic.findSectionAttendanceSummary(
                sectionId,
                jwt.getClaimAsStringList("roles"),
                jwt.getClaimAsString("lecturerId"));
    }

    @Operation(summary = "Chi tiết một bản ghi điểm danh cụ thể", description = "Truy xuất chi tiết trạng thái có mặt, vắng, ghi chú lý do của một bản ghi điểm danh")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tìm thấy bản ghi điểm danh")
    })
    @GetMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER', 'STUDENT')")
    public AttendanceResponse getAttendanceRecord(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh bản ghi điểm danh (UUID)", required = true) @PathVariable String id) {
        return academic.findOne(
                id,
                jwt.getClaimAsStringList("roles"),
                jwt.getClaimAsString("studentId"),
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
