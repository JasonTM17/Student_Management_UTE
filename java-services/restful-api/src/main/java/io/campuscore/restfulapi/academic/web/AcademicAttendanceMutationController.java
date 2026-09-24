package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicAttendanceWriteService;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceMutationDtos.AttendanceMutationResponse;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceMutationDtos.SectionAttendanceUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Role-protected academic attendance write routes. */
@Tag(name = "Attendance Operations", description = "Thao tác ghi nhận và cập nhật điểm danh lớp học phần của giảng viên")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/attendance")
public class AcademicAttendanceMutationController {

    private final AcademicAttendanceWriteService writeService;

    public AcademicAttendanceMutationController(AcademicAttendanceWriteService writeService) {
        this.writeService = writeService;
    }

    @Operation(summary = "Ghi nhận / cập nhật điểm danh theo lớp học phần", description = "Giảng viên phụ trách hoặc Quản trị viên cập nhật điểm danh hàng loạt cho sinh viên trong lớp")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ghi nhận điểm danh thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc sinh viên không thuộc lớp"),
        @ApiResponse(responseCode = "403", description = "Giảng viên không phụ trách lớp này"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy lớp học phần")
    })
    @PutMapping(path = {"sections/{sectionId}", "section/{sectionId}"})
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AttendanceMutationResponse> recordSectionAttendance(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh lớp học phần (UUID)", required = true) @PathVariable String sectionId,
            @Valid @RequestBody SectionAttendanceUpdateRequest request) {

        String lecturerId = jwt.getClaimAsString("lecturerId");
        List<String> roles = jwt.getClaimAsStringList("roles");

        AttendanceMutationResponse response = writeService.recordSectionAttendance(sectionId, lecturerId, roles, request);
        return ResponseEntity.ok(response);
    }
}
