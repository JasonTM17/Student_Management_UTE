package io.campuscore.restfulapi.notification.web;

import io.campuscore.restfulapi.notification.service.NotificationReadService;
import io.campuscore.restfulapi.notification.web.NotificationReadDtos.NotificationListResponse;
import io.campuscore.restfulapi.notification.web.NotificationReadDtos.UnreadCountResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feature-flagged, read-only notification boundary. Legacy mutation and
 * realtime routes remain owned by the notification service.
 */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.notifications-read", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/notifications")
public class NotificationReadController {

    private final NotificationReadService notifications;

    public NotificationReadController(NotificationReadService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("my")
    public NotificationListResponse getMyNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String isRead) {
        return notifications.findMy(subject(jwt), page, limit, isRead);
    }

    @GetMapping("my/unread-count")
    public UnreadCountResponse getMyUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        return notifications.getUnreadCount(subject(jwt));
    }

    private String subject(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }
}
