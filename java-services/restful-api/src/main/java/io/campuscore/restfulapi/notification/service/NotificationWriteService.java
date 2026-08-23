package io.campuscore.restfulapi.notification.service;

import io.campuscore.restfulapi.notification.repository.NotificationWriteRepository;
import io.campuscore.restfulapi.notification.repository.NotificationWriteRepository.CreateNotificationCommand;
import io.campuscore.restfulapi.notification.repository.NotificationWriteRepository.PatchValue;
import io.campuscore.restfulapi.notification.repository.NotificationWriteRepository.UpdateNotificationCommand;
import io.campuscore.restfulapi.notification.web.NotificationReadDtos.NotificationResponse;
import io.campuscore.restfulapi.notification.web.NotificationWriteDtos.CreateNotificationRequest;
import io.campuscore.restfulapi.notification.web.NotificationWriteDtos.DeleteNotificationResponse;
import io.campuscore.restfulapi.notification.web.NotificationWriteDtos.MarkAllReadResponse;
import io.campuscore.restfulapi.notification.web.NotificationWriteDtos.UpdateNotificationRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Current-user notification inbox mutation service. */
@Service
@Profile("persistence")
public class NotificationWriteService {

    private static final Set<String> TYPES = Set.of("INFO", "WARNING", "ERROR", "SUCCESS");

    private final NotificationWriteRepository notifications;
    private final Clock clock = Clock.systemUTC();

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
    public NotificationResponse create(CreateNotificationRequest request) {
        String type = requireText(request.type(), "type");
        if (!TYPES.contains(type)) {
            throw new IllegalArgumentException("type must be INFO, WARNING, ERROR, or SUCCESS");
        }
        Instant now = Instant.now(clock);
        return notifications.create(new CreateNotificationCommand(
                UUID.randomUUID().toString(),
                requireText(request.userId(), "userId"),
                requireText(request.title(), "title"),
                requireText(request.message(), "message"),
                type,
                request.link(),
                now));
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

    @Transactional
    public NotificationResponse update(String notificationId, UpdateNotificationRequest request) {
        String id = requireText(notificationId, "notification id");
        notifications.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (request.has("type") && !TYPES.contains(request.type())) {
            throw new IllegalArgumentException("type must be INFO, WARNING, ERROR, or SUCCESS");
        }
        notifications.update(new UpdateNotificationCommand(
                id,
                patch(request, "userId", textPatch(request, "userId", request.userId())),
                patch(request, "title", textPatch(request, "title", request.title())),
                patch(request, "message", textPatch(request, "message", request.message())),
                patch(request, "type", request.type()),
                patch(request, "link", request.link())));
        return notifications.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    private static void requireSubject(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Authenticated subject is required");
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private static String textPatch(UpdateNotificationRequest request, String field, String value) {
        if (!request.has(field)) {
            return null;
        }
        return requireText(value, field);
    }

    private static PatchValue<String> patch(UpdateNotificationRequest request, String field, String value) {
        return request.has(field) ? PatchValue.present(value) : PatchValue.omitted();
    }
}
