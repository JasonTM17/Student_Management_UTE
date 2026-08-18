package io.campuscore.notification.web;

import io.campuscore.notification.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("my")
    public Page<NotificationDtos.NotificationResponse> getMyNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) Boolean isRead) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return notifications.findMy(userId, page, limit, isRead);
    }

    @GetMapping("my/unread-count")
    public NotificationDtos.UnreadCountResponse getMyUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return notifications.getUnreadCount(userId);
    }

    @PatchMapping("my/{id}/read")
    public NotificationDtos.MarkReadResponse markMyNotificationRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return notifications.markRead(userId, id);
    }

    @PatchMapping("my/read-all")
    public NotificationDtos.MarkAllReadResponse markAllMyNotificationsRead(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return notifications.markAllRead(userId);
    }

    @DeleteMapping("my/{id}")
    public ResponseEntity<NotificationDtos.DeleteResponse> deleteMyNotification(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        notifications.delete(userId, id);
        return ResponseEntity.ok(new NotificationDtos.DeleteResponse("Notification deleted successfully"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public NotificationDtos.NotificationResponse create(@Valid @RequestBody NotificationDtos.CreateNotificationRequest request) {
        return notifications.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Page<NotificationDtos.NotificationResponse> findAll(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) UUID userId) {
        return notifications.findAll(page, limit, userId);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public NotificationDtos.NotificationResponse findOne(@PathVariable UUID id) {
        return notifications.findOne(id);
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<NotificationDtos.DeleteResponse> remove(@PathVariable UUID id) {
        notifications.remove(id);
        return ResponseEntity.ok(new NotificationDtos.DeleteResponse("Notification deleted successfully"));
    }
}
