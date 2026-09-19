package io.campuscore.restfulapi.notification.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.campuscore.restfulapi.notification.service.NotificationWriteService;
import io.campuscore.restfulapi.notification.web.NotificationReadDtos.NotificationResponse;
import io.campuscore.restfulapi.notification.web.NotificationWriteDtos.CreateNotificationRequest;
import io.campuscore.restfulapi.notification.web.NotificationWriteDtos.DeleteNotificationResponse;
import io.campuscore.restfulapi.notification.web.NotificationWriteDtos.MarkAllReadResponse;
import io.campuscore.restfulapi.notification.web.NotificationWriteDtos.UpdateNotificationRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Notification inbox mutation routes owned by the Java API. */
@Tag(name = "Notifications & Alerts", description = "Hộp thư thông báo cá nhân, đếm tin chưa đọc và quản trị phát thông báo hệ thống")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/notifications")
public class NotificationWriteController {

    private final NotificationWriteService notifications;

    public NotificationWriteController(NotificationWriteService notifications) {
        this.notifications = notifications;
    }

    @Operation(summary = "Đánh dấu thông báo đã đọc", description = "Cập nhật trạng thái đã đọc cho một thông báo trong hộp thư cá nhân")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Đánh dấu thành công")
    })
    @PatchMapping("my/{id}/read")
    public NotificationResponse markMyNotificationRead(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh thông báo (UUID)", required = true) @PathVariable String id) {
        return notifications.markRead(subject(jwt), id);
    }

    @Operation(summary = "Đánh dấu tất cả thông báo đã đọc", description = "Đánh dấu đã đọc cho toàn bộ thông báo trong hộp thư cá nhân")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Đánh dấu tất cả thành công")
    })
    @PatchMapping("my/read-all")
    public MarkAllReadResponse markAllMyNotificationsRead(@AuthenticationPrincipal Jwt jwt) {
        return notifications.markAllRead(subject(jwt));
    }

    @Operation(summary = "Phát thông báo hệ thống mới (Admin)", description = "Quản trị viên gửi thông báo đến một hoặc nhiều người dùng")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Tạo thông báo thành công")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse create(@RequestBody JsonNode body) {
        return notifications.create(CreateNotificationRequest.from(body));
    }

    @Operation(summary = "Xóa thông báo khỏi hộp thư cá nhân", description = "Người dùng xóa một thông báo khỏi hộp thư cá nhân của mình")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Xóa thành công")
    })
    @DeleteMapping("my/{id}")
    public DeleteNotificationResponse deleteMyNotification(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh thông báo (UUID)", required = true) @PathVariable String id) {
        return notifications.deleteMyNotification(subject(jwt), id);
    }

    @Operation(summary = "Quản trị xóa thông báo hệ thống (Admin)", description = "Quản trị viên xóa thông báo khỏi toàn hệ thống")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Xóa thành công")
    })
    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public DeleteNotificationResponse delete(
            @Parameter(description = "Mã định danh thông báo (UUID)", required = true) @PathVariable String id) {
        return notifications.delete(id);
    }

    @Operation(summary = "Quản trị cập nhật thông báo (Admin)", description = "Quản trị viên sửa đổi tiêu đề, nội dung thông báo hệ thống")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cập nhật thành công")
    })
    @PutMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public NotificationResponse update(
            @Parameter(description = "Mã định danh thông báo (UUID)", required = true) @PathVariable String id,
            @RequestBody JsonNode body) {
        return notifications.update(id, UpdateNotificationRequest.from(body));
    }

    private String subject(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }
}
