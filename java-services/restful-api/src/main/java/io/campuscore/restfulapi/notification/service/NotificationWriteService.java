package io.campuscore.restfulapi.notification.service;

import io.campuscore.restfulapi.notification.repository.NotificationWriteRepository;
import io.campuscore.restfulapi.notification.web.NotificationReadDtos.NotificationResponse;
import io.campuscore.restfulapi.notification.web.NotificationWriteDtos.DeleteNotificationResponse;
import io.campuscore.restfulapi.notification.web.NotificationWriteDtos.MarkAllReadResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Write candidate for current-user notification self-service routes. */
@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.notifications-write", name = "enabled", havingValue = "true")
public class NotificationWriteService {

    private final NotificationWriteRepository notifications;

    public NotificationWriteService(NotificationWriteRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional
    public NotificationResponse markRead(String userId, String notificationId) {
        requireSubject(userId);
        requireText(notificationId, "notification id");
        NotificationResponse existing = notifications.findOwned(userId, notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (existing.isRead()) {
            return existing;
        }
        notifications.markRead(notificationId);
        return notifications.findOwned(userId, notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    @Transactional
    public MarkAllReadResponse markAllRead(String userId) {
        requireSubject(userId);
        return new MarkAllReadResponse(notifications.markAllRead(userId));
    }

    @Transactional
    public DeleteNotificationResponse deleteMyNotification(String userId, String notificationId) {
        requireSubject(userId);
        requireText(notificationId, "notification id");
        if (notifications.findOwned(userId, notificationId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete this notification");
        }
        notifications.delete(notificationId);
        return new DeleteNotificationResponse("Notification deleted successfully");
    }

    @Transactional
    public DeleteNotificationResponse delete(String notificationId) {
        requireText(notificationId, "notification id");
        if (notifications.findById(notificationId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        notifications.delete(notificationId);
        return new DeleteNotificationResponse("Notification deleted successfully");
    }

    private static void requireSubject(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Authenticated subject is required");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }
}
