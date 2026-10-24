package io.campuscore.restfulapi.thesis.controller;

import io.campuscore.restfulapi.thesis.service.ThesisDeanOverviewService;
import io.campuscore.restfulapi.thesis.dto.ThesisDeanOverviewDtos.DeanOverviewResponse;
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
import io.campuscore.restfulapi.thesis.dto.ThesisDeanOverviewDtos;

@Tag(name = "Thesis Dean Overview", description = "Bảng quan sát khóa luận toàn khoa dành cho Trưởng khoa: nhóm, thành viên và giảng viên hướng dẫn")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/thesis/dean")
public class ThesisDeanController {

    private final ThesisDeanOverviewService overview;

    public ThesisDeanController(ThesisDeanOverviewService overview) {
        this.overview = overview;
    }

    @Operation(summary = "Quan sát toàn bộ nhóm khóa luận trong khoa", description = "Trả về danh sách nhóm, thành viên, giảng viên hướng dẫn và thống kê theo đợt, giới hạn trong khoa của Trưởng khoa")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy xuất tổng quan thành công"),
        @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
        @ApiResponse(responseCode = "403", description = "Không phải Trưởng khoa hoặc thiếu hồ sơ giảng viên đang hoạt động"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy đợt khóa luận")
    })
    @GetMapping("/overview")
    @PreAuthorize("hasRole('TRUONG_KHOA')")
    public DeanOverviewResponse overview(
            @Parameter(description = "Mã UUID của đợt khóa luận", required = true)
            @RequestParam UUID roundId,
            @AuthenticationPrincipal Jwt actor) {
        return overview.overview(roundId, actor);
    }
}
