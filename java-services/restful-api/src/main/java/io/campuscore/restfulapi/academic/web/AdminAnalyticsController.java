package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AdminAnalyticsService;
import io.campuscore.restfulapi.academic.web.AdminAnalyticsDtos.AnalyticsOverview;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrator dashboard aggregates (students, courses, enrollments, grades). */
@Tag(name = "Admin Analytics", description = "Tổng hợp số liệu học vụ toàn trường cho bảng điều khiển quản trị: khoa, học kỳ, học hàm giảng viên, phân bố điểm và tổng số")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/academic/distribution-overview")
public class AdminAnalyticsController {

    private final AdminAnalyticsService analytics;

    public AdminAnalyticsController(AdminAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @Operation(summary = "Tổng quan phân tích dành cho quản trị (Admin/SuperAdmin)", description = "Số liệu theo khoa, xu hướng học kỳ theo học kỳ, học hàm giảng viên, phân bố điểm chữ và tổng số thực thể học vụ")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy tổng quan phân tích thành công"),
        @ApiResponse(responseCode = "403", description = "Chỉ Quản trị viên hoặc Quản trị viên cấp cao")
    })
    @GetMapping("overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AnalyticsOverview overview() {
        return analytics.overview();
    }
}
