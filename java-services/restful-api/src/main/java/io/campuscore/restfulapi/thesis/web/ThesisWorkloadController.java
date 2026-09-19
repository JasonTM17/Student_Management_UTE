package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.service.ThesisLecturerWorkloadService;
import io.campuscore.restfulapi.thesis.service.ThesisLecturerWorkloadService.LecturerWorkload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lecturer-scoped thesis workload for the campus assistant (read-only, self-scoped). */
@Tag(name = "Thesis Lecturer Workload", description = "Thống kê khối lượng hướng dẫn khóa luận, phản biện và hội đồng của giảng viên")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/thesis")
public class ThesisWorkloadController {

    private final ThesisLecturerWorkloadService workloadService;

    public ThesisWorkloadController(ThesisLecturerWorkloadService workloadService) {
        this.workloadService = workloadService;
    }

    @Operation(summary = "Xem khối lượng công việc khóa luận của giảng viên", description = "Truy xuất danh sách đề tài đang hướng dẫn, đề tài được phân công phản biện và các hội đồng tham gia")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy xuất khối lượng công việc thành công"),
        @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
        @ApiResponse(responseCode = "403", description = "Chỉ giảng viên mới có quyền truy cập")
    })
    @GetMapping("/me/workload")
    @PreAuthorize("hasRole('LECTURER')")
    public LecturerWorkload myWorkload(@AuthenticationPrincipal Jwt actor) {
        String lecturerId = actor == null ? null : actor.getClaimAsString("lecturerId");
        if (!StringUtils.hasText(lecturerId)) {
            return new LecturerWorkload(java.util.List.of(), java.util.List.of(), java.util.List.of());
        }
        return workloadService.workload(lecturerId);
    }
}
