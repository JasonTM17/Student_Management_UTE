package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.service.ThesisProgressService;
import io.campuscore.restfulapi.thesis.web.ThesisProgressDtos.ProgressResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Thesis Student Progress", description = "Theo dõi tiến độ thực hiện khóa luận của sinh viên và nhóm theo các mốc học thuật")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/thesis/me")
public class ThesisProgressController {

    private final ThesisProgressService progress;

    public ThesisProgressController(ThesisProgressService progress) {
        this.progress = progress;
    }

    @Operation(summary = "Xem tiến độ khóa luận của cá nhân/nhóm", description = "Truy xuất trạng thái hiện tại của sinh viên trong đợt khóa luận (chưa vào nhóm, đã chọn đề tài, đã nộp báo cáo, đã bảo vệ)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy xuất tiến độ thành công"),
        @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
        @ApiResponse(responseCode = "403", description = "Chỉ sinh viên mới có quyền xem tiến độ cá nhân")
    })
    @GetMapping("/progress")
    @PreAuthorize("hasRole('STUDENT')")
    public ProgressResponse get(
            @Parameter(description = "Mã UUID của đợt khóa luận", required = true)
            @RequestParam UUID roundId,
            @AuthenticationPrincipal Jwt actor) {
        return progress.get(roundId, actor);
    }
}
