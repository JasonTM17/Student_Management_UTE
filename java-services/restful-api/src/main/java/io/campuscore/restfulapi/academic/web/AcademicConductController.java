package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicConductService;
import io.campuscore.restfulapi.academic.web.AcademicConductDtos.ConductSemesterScoreDto;
import io.campuscore.restfulapi.academic.web.AcademicConductDtos.StudentConductSummaryDto;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller providing UTE Student Conduct / Training Points ("Điểm rèn luyện" - DRL).
 * Route and access-control concerns only; queries and the MOET criteria
 * catalogue live in {@link AcademicConductService}.
 */
@Tag(name = "Student Conduct & Training Points", description = "Điểm rèn luyện sinh viên theo 5 điều khung quy chế Bộ GD&ĐT và Quy chế công tác sinh viên HCM-UTE")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/conduct")
public class AcademicConductController {

    private final AcademicConductService conduct;

    public AcademicConductController(AcademicConductService conduct) {
        this.conduct = conduct;
    }

    @Operation(summary = "Xem tổng kết điểm rèn luyện cá nhân của sinh viên", description = "Truy vấn điểm rèn luyện tích lũy, xếp loại và lịch sử qua các học kỳ của sinh viên đang đăng nhập")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy tổng kết điểm rèn luyện thành công"),
        @ApiResponse(responseCode = "403", description = "Chỉ sinh viên mới có quyền truy cập")
    })
    @GetMapping("my")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentConductSummaryDto getMyConductSummary(@AuthenticationPrincipal Jwt jwt) {
        String studentProfileId = resolveStudentProfileId(jwt);
        return conduct.studentSummary(studentProfileId);
    }

    /**
     * LEC-P1-5: a lecturer may read conduct only for students they actually teach.
     *
     * <p>Without this, any lecturer could read every student's conduct record by id
     * — the same over-broad read the attendance endpoints already avoided with their
     * section-ownership check. Staff keep the full read; the endpoint exists so a
     * lecturer can look at their own class, not so it can serve as a directory of
     * the student body.
     */
    @Operation(summary = "Xem điểm rèn luyện của sinh viên (Admin / Giảng viên phụ trách)", description = "Quản trị viên hoặc Giảng viên giảng dạy xem chi tiết điểm rèn luyện của sinh viên theo mã SV")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy thông tin điểm rèn luyện thành công"),
        @ApiResponse(responseCode = "403", description = "Không có thẩm quyền xem sinh viên ngoài danh sách lớp phụ trách"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy hồ sơ sinh viên")
    })
    @GetMapping("student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER')")
    public StudentConductSummaryDto getStudentConductSummary(
            @Parameter(description = "Mã định danh hoặc Mã số sinh viên (MSSV)", required = true) @PathVariable String studentId,
            @AuthenticationPrincipal Jwt actor) {
        requireConductReadAccess(studentId, actor);
        return conduct.studentSummary(studentId);
    }

    private void requireConductReadAccess(String studentId, Jwt actor) {
        List<String> roles = actor == null ? null : actor.getClaimAsStringList("roles");
        if (roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN"))) {
            return;
        }
        String lecturerId = actor == null ? null : actor.getClaimAsString("lecturerId");
        if (lecturerId == null || lecturerId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "CONDUCT_READ_REQUIRES_SECTION_OWNERSHIP");
        }
        if (!conduct.isStudentTaughtByLecturer(studentId, lecturerId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "CONDUCT_READ_REQUIRES_SECTION_OWNERSHIP");
        }
    }

    @Operation(summary = "Xem chi tiết điểm rèn luyện học kỳ của cá nhân", description = "Truy xuất chi tiết điểm 5 tiêu chí thành phần và danh sách hoạt động phong trào sinh viên đã tham gia trong học kỳ")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy điểm học kỳ thành công"),
        @ApiResponse(responseCode = "404", description = "Không có dữ liệu điểm rèn luyện trong học kỳ")
    })
    @GetMapping("my/semester/{semesterId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ConductSemesterScoreDto getMySemesterScore(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh học kỳ (UUID)", required = true) @PathVariable String semesterId) {
        String studentProfileId = resolveStudentProfileId(jwt);
        return conduct.semesterScore(studentProfileId, semesterId);
    }

    private String resolveStudentProfileId(Jwt jwt) {
        String studentIdClaim = jwt.getClaimAsString("studentId");
        if (studentIdClaim != null && !studentIdClaim.isBlank()) {
            return studentIdClaim;
        }
        String profileId = conduct.profileIdForUser(jwt.getSubject());
        if (profileId != null) {
            return profileId;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED");
    }
}
