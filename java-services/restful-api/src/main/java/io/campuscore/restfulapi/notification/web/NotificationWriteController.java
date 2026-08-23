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

/** Notification inbox mutation routes owned by the Java API. */
@RestController
@Profile("persistence")
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

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse create(@RequestBody JsonNode body) {
        return notifications.create(CreateNotificationRequest.from(body));
    }

    @DeleteMapping("my/{id}")
    public DeleteNotificationResponse deleteMyNotification(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        return notifications.deleteMyNotification(subject(jwt), id);
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public DeleteNotificationResponse delete(@PathVariable String id) {
        return notifications.delete(id);
    }

    @PutMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public NotificationResponse update(
            @PathVariable String id,
            @RequestBody JsonNode body) {
        return notifications.update(id, UpdateNotificationRequest.from(body));
    }

    private String subject(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }
}
