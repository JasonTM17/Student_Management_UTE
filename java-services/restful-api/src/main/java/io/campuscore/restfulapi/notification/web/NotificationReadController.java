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

/** Notification inbox query routes owned by the Java API. */
@RestController
@Profile("persistence")
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

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public NotificationListResponse findAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String userId) {
        return notifications.findAll(page, limit, userId);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public NotificationResponse findOne(@PathVariable String id) {
        return notifications.findOne(id);
    }

    private String subject(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }
}
