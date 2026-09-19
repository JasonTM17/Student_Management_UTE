package io.campuscore.restfulapi.notification.web;

import io.campuscore.restfulapi.notification.service.NotificationReadService;
import io.campuscore.restfulapi.notification.web.NotificationReadDtos.NotificationListResponse;
import io.campuscore.restfulapi.notification.web.NotificationReadDtos.NotificationResponse;
import io.campuscore.restfulapi.notification.web.NotificationReadDtos.UnreadCountResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

/** Notification inbox query routes owned by the Java API. */
@Tag(name = "Notifications & Alerts", description = "Hộp thư thông báo cá nhân, đếm tin chưa đọc và quản trị phát thông báo hệ thống")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/notifications")
public class NotificationReadController {

    private final NotificationReadService notifications;

    public NotificationReadController(NotificationReadService notifications) {
        this.notifications = notifications;
    }

    @Operation(summary = "Hộp thư thông báo cá nhân của người dùng", description = "Truy vấn danh sách thông báo theo trạng thái đã đọc hoặc chưa đọc")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy vấn thành công")
    })
    @GetMapping("my")
    public NotificationListResponse getMyNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Số trang") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Lọc đã đọc (true/false)") @RequestParam(required = false) String isRead) {
        return notifications.findMy(subject(jwt), page, limit, isRead);
    }

    @Operation(summary = "Đếm số lượng thông báo chưa đọc", description = "Lấy số lượng thông báo chưa đọc để hiển thị trên huy hiệu (badge) giao diện")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Đếm thành công")
    })
    @GetMapping("my/unread-count")
    public UnreadCountResponse getMyUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        return notifications.getUnreadCount(subject(jwt));
    }

    @Operation(summary = "Quản trị tra cứu thông báo toàn hệ thống (Admin)", description = "Truy vấn danh sách thông báo hệ thống theo người nhận")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy vấn thành công")
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public NotificationListResponse findAll(
            @Parameter(description = "Số trang") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Mã định danh người dùng nhận (UUID)") @RequestParam(required = false) String userId) {
        return notifications.findAll(page, limit, userId);
    }

    @Operation(summary = "Chi tiết một thông báo", description = "Lấy chi tiết nội dung và siêu dữ liệu của một thông báo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tìm thấy thông báo")
    })
    @GetMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public NotificationResponse findOne(
            @Parameter(description = "Mã định danh thông báo (UUID)", required = true) @PathVariable String id) {
        return notifications.findOne(id);
    }

    private String subject(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }
}
