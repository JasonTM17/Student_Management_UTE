package io.campuscore.restfulapi.notification.web;

import io.campuscore.restfulapi.notification.service.NotificationWriteService;
import io.campuscore.restfulapi.notification.web.NotificationReadDtos.NotificationResponse;
import io.campuscore.restfulapi.notification.web.NotificationWriteDtos.DeleteNotificationResponse;
import io.campuscore.restfulapi.notification.web.NotificationWriteDtos.MarkAllReadResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feature-flagged notification self-service writer.
 *
 * <p>The legacy notification service remains the public owner until PostgreSQL
 * parity, event/realtime behavior, canary routing and rollback have been
 * rehearsed.</p>
 */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.notifications-write", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/notifications")
public class NotificationWriteController {

    private final NotificationWriteService notifications;

    public NotificationWriteController(NotificationWriteService notifications) {
        this.notifications = notifications;
    }

    @PatchMapping("my/{id}/read")
    public NotificationResponse markMyNotificationRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        return notifications.markRead(subject(jwt), id);
    }

    @PatchMapping("my/read-all")
    public MarkAllReadResponse markAllMyNotificationsRead(@AuthenticationPrincipal Jwt jwt) {
        return notifications.markAllRead(subject(jwt));
    }

    @DeleteMapping("my/{id}")
    public DeleteNotificationResponse deleteMyNotification(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        return notifications.deleteMyNotification(subject(jwt), id);
    }

    private String subject(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }
}
