package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentListResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.GradeItemResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.GradeSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.StudentGradeSectionRow;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.StudentGradesByEnrollmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.TranscriptResponse;
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

/** Student, lecturer and administrator enrollment and grade read routes. */
@Tag(name = "Enrollments & Academic Records", description = "Tra cứu ghi danh học phần, điểm số sinh viên, bảng điểm tích lũy (Transcript) và đầu điểm lớp học phần")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1")
public class AcademicEnrollmentReadController {

    private final AcademicEnrollmentReadService academic;

    public AcademicEnrollmentReadController(AcademicEnrollmentReadService academic) {
        this.academic = academic;
    }

    @Operation(summary = "Danh sách học phần đã ghi danh của sinh viên", description = "Lấy danh sách các lớp học phần sinh viên đang theo học trong học kỳ hoặc toàn khóa")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy vấn thành công")
    })
    @GetMapping("enrollments/my")
    @PreAuthorize("hasRole('STUDENT')")
    public List<EnrollmentResponse> getMyEnrollments(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh học kỳ (UUID)") @RequestParam(required = false) String semesterId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("semesterId"));
        return academic.findStudentEnrollments(jwt.getClaimAsString("studentId"), semesterId);
    }

    @Operation(summary = "Xem điểm học phần theo học kỳ của sinh viên", description = "Truy vấn kết quả điểm quá trình, điểm thi kết thúc học phần và điểm chữ học kỳ")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy bảng điểm học kỳ thành công")
    })
    @GetMapping("enrollments/my/grades")
    @PreAuthorize("hasRole('STUDENT')")
    public List<GradeSummary> getMyGrades(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh học kỳ (UUID)") @RequestParam(required = false) String semesterId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("semesterId"));
        return academic.findStudentGrades(jwt.getClaimAsString("studentId"), semesterId);
    }

    @Operation(summary = "Xem toàn bộ bảng điểm tích lũy học tập (Transcript)", description = "Truy xuất đầy đủ bảng điểm toàn khóa, điểm trung bình tích lũy GPA/CPA hệ 4 và hệ 10")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy bảng điểm tích lũy thành công")
    })
    @GetMapping("enrollments/my/transcript")
    @PreAuthorize("hasRole('STUDENT')")
    public TranscriptResponse getMyTranscript(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return academic.findStudentTranscript(jwt.getClaimAsString("studentId"));
    }

    @Operation(summary = "Quản trị tra cứu danh sách ghi danh của một sinh viên", description = "Phòng Đào tạo / Admin tra cứu toàn bộ môn học một sinh viên đã ghi danh")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy vấn thành công")
    })
    @GetMapping("enrollments/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public List<EnrollmentResponse> getStudentEnrollments(
            @Parameter(description = "Mã định danh sinh viên (UUID)", required = true) @PathVariable String studentId,
            @Parameter(description = "Mã định danh học kỳ (UUID)") @RequestParam(required = false) String semesterId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("semesterId"));
        return academic.findStudentEnrollments(studentId, semesterId);
    }

    @Operation(summary = "Quản trị tra cứu danh sách ghi danh toàn hệ thống", description = "Truy vấn danh sách ghi danh với bộ lọc trạng thái, học kỳ, sinh viên, học phần, lớp học phần")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy vấn thành công")
    })
    @GetMapping("enrollments")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public EnrollmentListResponse getEnrollments(
            @Parameter(description = "Số trang") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Trạng thái ghi danh (ENROLLED, DROPPED, COMPLETED)") @RequestParam(required = false) String status,
            @Parameter(description = "Mã định danh học kỳ (UUID)") @RequestParam(required = false) String semesterId,
            @Parameter(description = "Mã định danh sinh viên (UUID)") @RequestParam(required = false) String studentId,
            @Parameter(description = "Mã định danh môn học (UUID)") @RequestParam(required = false) String courseId,
            @Parameter(description = "Mã định danh lớp học phần (UUID)") @RequestParam(required = false) String sectionId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(
                queryParameters,
                Set.of("page", "limit", "status", "semesterId", "studentId", "courseId", "sectionId"));
        return academic.findEnrollments(page, limit, status, semesterId, studentId, courseId, sectionId);
    }

    @Operation(summary = "Chi tiết một bản ghi ghi danh học phần", description = "Lấy thông tin chi tiết một lượt ghi danh của sinh viên")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tìm thấy thông tin ghi danh")
    })
    @GetMapping("enrollments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'STUDENT')")
    public EnrollmentResponse getEnrollment(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh ghi danh (UUID)", required = true) @PathVariable String id) {
        return academic.findEnrollment(id, jwt.getClaimAsStringList("roles"), jwt.getClaimAsString("studentId"));
    }

    @Operation(summary = "Danh sách cấu trúc đầu điểm của lớp học phần", description = "Truy xuất danh sách các cột điểm thành phần (chuyên cần, giữa kỳ, đồ án, cuối kỳ)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy cấu trúc đầu điểm thành công")
    })
    @GetMapping("grades/items/section/{sectionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER')")
    public List<GradeItemResponse> getGradeItemsBySection(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh lớp học phần (UUID)", required = true) @PathVariable String sectionId) {
        return academic.findGradeItemsBySection(sectionId, jwt.getClaimAsStringList("roles"), jwt.getClaimAsString("lecturerId"));
    }

    @Operation(summary = "Cấu trúc đầu điểm do giảng viên phụ trách", description = "Truy xuất danh mục các đầu điểm thành phần của các lớp do giảng viên đang đăng nhập giảng dạy")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy vấn thành công")
    })
    @GetMapping("grades/items/lecturer/my")
    @PreAuthorize("hasRole('LECTURER')")
    public List<GradeItemResponse> getMyGradeItems(@AuthenticationPrincipal Jwt jwt) {
        return academic.findGradeItemsByLecturer(jwt.getClaimAsString("lecturerId"));
    }

    @Operation(summary = "Bảng điểm chi tiết sinh viên trong lớp học phần", description = "Giảng viên hoặc Quản trị viên xem bảng điểm chi tiết các đầu điểm của tất cả sinh viên trong lớp")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy bảng điểm lớp học phần thành công")
    })
    @GetMapping("grades/student-grades/section/{sectionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER')")
    public List<StudentGradeSectionRow> getStudentGradesBySection(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh lớp học phần (UUID)", required = true) @PathVariable String sectionId) {
        return academic.findStudentGradesBySection(sectionId, jwt.getClaimAsStringList("roles"), jwt.getClaimAsString("lecturerId"));
    }

    @Operation(summary = "Tra cứu điểm sinh viên các lớp do giảng viên phụ trách", description = "Lấy danh sách bảng điểm của các lớp học phần do giảng viên đang giảng dạy")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy vấn thành công")
    })
    @GetMapping("grades/student-grades/lecturer/my")
    @PreAuthorize("hasRole('LECTURER')")
    public List<StudentGradeSectionRow> getMyStudentGrades(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh lớp học phần (UUID)") @RequestParam(required = false) String sectionId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("sectionId"));
        return academic.findStudentGradesByLecturer(jwt.getClaimAsString("lecturerId"), sectionId);
    }

    @Operation(summary = "Chi tiết các con điểm thành phần của một lượt ghi danh", description = "Tra cứu chi tiết từng con điểm thành phần của một sinh viên trong một môn học cụ thể")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy chi tiết điểm thành công")
    })
    @GetMapping("grades/student-grades/enrollment/{enrollmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER', 'STUDENT')")
    public StudentGradesByEnrollmentResponse getStudentGradesByEnrollment(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh ghi danh (UUID)", required = true) @PathVariable String enrollmentId) {
        return academic.findStudentGradesByEnrollment(
                enrollmentId,
                jwt.getClaimAsStringList("roles"),
                jwt.getClaimAsString("lecturerId"),
                jwt.getClaimAsString("studentId"));
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
