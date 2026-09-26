package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicMutationService;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicMutationDtos.EnrollRequest;
import io.campuscore.restfulapi.academic.web.AcademicMutationDtos.GradeUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Academic Operations & Grade Mutations", description = "Thao tác ghi danh học phần, xuất báo cáo CSV và quy trình nhập/công bố bảng điểm của giảng viên")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1")
public class AcademicMutationController {

    private final AcademicMutationService mutations;

    public AcademicMutationController(AcademicMutationService mutations) {
        this.mutations = mutations;
    }

    @Operation(summary = "Đăng ký học phần (Legacy Endpoint)", description = "Đăng ký sinh viên vào lớp học phần qua cổng mutation legacy")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Đăng ký thành công")
    })
    @PostMapping("enrollments/enroll")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<EnrollmentResponse> enroll(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Khóa chống trùng lặp Idempotency") @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody EnrollRequest request) {
        EnrollmentResponse body = mutations.enroll(
                jwt.getClaimAsString("studentId"),
                request.sectionId(),
                jwt.getClaimAsStringList("roles"),
                idempotencyKey);
        return ResponseEntity.ok()
                .header("Deprecation", "true")
                .body(body);
    }

    @Operation(summary = "Hủy ghi danh học phần (Legacy Endpoint)", description = "Rút khỏi lớp học phần đã đăng ký qua cổng mutation legacy")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Hủy thành công")
    })
    @PostMapping("enrollments/{id}/drop")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> drop(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh ghi danh (UUID)", required = true) @PathVariable String id,
            @Parameter(description = "Khóa chống trùng lặp Idempotency") @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        mutations.drop(id, jwt.getClaimAsString("studentId"), jwt.getClaimAsStringList("roles"), idempotencyKey);
        return ResponseEntity.ok()
                .header("Deprecation", "true")
                .body(Map.of("message", "Enrollment dropped successfully"));
    }

    @Operation(summary = "Quản trị xóa vĩnh viễn bản ghi ghi danh", description = "Xóa một bản ghi ghi danh của sinh viên khỏi hệ thống học vụ và ghi vết hành vi quản trị (EnrollmentEvent + AdminAudit)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Xóa thành công")
    })
    @DeleteMapping("enrollments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Map<String, String> deleteEnrollment(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh ghi danh (UUID)", required = true) @PathVariable String id) {
        mutations.deleteEnrollment(id, jwt != null ? jwt.getSubject() : null);
        return Map.of("message", "Enrollment deleted successfully");
    }

    @Operation(summary = "Xuất danh sách ghi danh ra file CSV", description = "Tạo báo cáo dạng CSV phục vụ công tác thống kê học vụ")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Xuất file CSV thành công")
    })
    @GetMapping(value = "enrollments/export/csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String exportEnrollments(
            @Parameter(description = "Trạng thái ghi danh") @RequestParam(required = false) String status,
            @Parameter(description = "Mã định danh học kỳ (UUID)") @RequestParam(required = false) String semesterId,
            @Parameter(description = "Mã định danh sinh viên (UUID)") @RequestParam(required = false) String studentId,
            @Parameter(description = "Mã định danh môn học (UUID)") @RequestParam(required = false) String courseId,
            @Parameter(description = "Mã định danh lớp học phần (UUID)") @RequestParam(required = false) String sectionId) {
        return mutations.exportEnrollments(status, semesterId, studentId, courseId, sectionId);
    }

    @Operation(summary = "Nhập hoặc cập nhật điểm học phần dạng bản nháp (Draft)", description = "Giảng viên lưu bảng điểm của lớp học phần dưới dạng bản nháp để chỉnh sửa trước khi công bố")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lưu bản nháp thành công")
    })
    @PutMapping("sections/{sectionId}/grades")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, String> updateGrades(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh lớp học phần (UUID)", required = true) @PathVariable String sectionId,
            @Valid @RequestBody GradeUpdateRequest request) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        mutations.updateGrades(sectionId, jwt.getClaimAsString("lecturerId"), isAdmin(roles), jwt.getSubject(), request.grades());
        return Map.of("message", "Grades saved as draft");
    }

    @Operation(summary = "Công bố chính thức điểm học phần (Publish)", description = "Chuyển bảng điểm từ bản nháp sang công bố chính thức, cho phép sinh viên tra cứu")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Công bố bảng điểm thành công")
    })
    @PostMapping("sections/{sectionId}/grades/publish")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, String> publishGrades(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh lớp học phần (UUID)", required = true) @PathVariable String sectionId) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        mutations.publishGrades(sectionId, jwt.getClaimAsString("lecturerId"), isAdmin(roles), jwt.getSubject());
        return Map.of("message", "Grades published successfully");
    }

    private static boolean isAdmin(List<String> roles) {
        return roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN"));
    }
}
