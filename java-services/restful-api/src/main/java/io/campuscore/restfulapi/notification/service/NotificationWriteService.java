package io.campuscore.restfulapi.notification.service;

import io.campuscore.restfulapi.audit.AdminAuditRecorder;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final AdminAuditRecorder audit;
    private final Clock clock = Clock.systemUTC();

    public NotificationWriteService(NotificationWriteRepository notifications, AdminAuditRecorder audit) {
        this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional
    public NotificationResponse markRead(String userId, String notificationId) {
        requireSubject(userId);
        requireText(notificationId, "notification id");
        if (notifications.findById(notificationId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        NotificationResponse existing = notifications.findOwned(userId, notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot modify this notification"));
        if (existing.isRead()) {
            return existing;
        }
        if (notifications.markRead(userId, notificationId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        return notifications.findOwned(userId, notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    @Transactional
    public MarkAllReadResponse markAllRead(String userId) {
        requireSubject(userId);
        return new MarkAllReadResponse(notifications.markAllRead(userId));
    }

    @Transactional
    public NotificationResponse create(CreateNotificationRequest request, String actorId) {
        String type = requireText(request.type(), "type");
        if (!TYPES.contains(type)) {
            throw new IllegalArgumentException("type must be INFO, WARNING, ERROR, or SUCCESS");
        }
        Instant now = Instant.now(clock);
        NotificationResponse created = notifications.create(new CreateNotificationCommand(
                UUID.randomUUID().toString(),
                requireText(request.userId(), "userId"),
                requireText(request.title(), "title"),
                requireText(request.message(), "message"),
                type,
                request.link(),
                now));
        audit.record(actorId, null, "NOTIFICATION_CREATED", "NOTIFICATION", created.id(),
                "Notification " + created.id() + " created for user " + created.userId()
                        + " (type " + created.type() + ")",
                null,
                notificationState(created));
        return created;
    }

    @Transactional
    public DeleteNotificationResponse deleteMyNotification(String userId, String notificationId) {
        requireSubject(userId);
        requireText(notificationId, "notification id");
        // Round-11 honesty split: a notification that does not exist is a 404;
        // only one that exists but belongs to someone else is a 403.
        if (notifications.findById(notificationId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        if (notifications.findOwned(userId, notificationId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete this notification");
        }
        if (notifications.delete(userId, notificationId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete this notification");
        }
        return new DeleteNotificationResponse("Notification deleted successfully");
    }

    @Transactional
    public DeleteNotificationResponse delete(String notificationId, String actorId) {
        String id = requireText(notificationId, "notification id");
        NotificationResponse existing = notifications.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (notifications.deleteAny(id) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        audit.record(actorId, null, "NOTIFICATION_DELETED", "NOTIFICATION", id,
                "Notification " + id + " deleted (was addressed to user " + existing.userId() + ")",
                notificationState(existing),
                null);
        return new DeleteNotificationResponse("Notification deleted successfully");
    }

    @Transactional
    public NotificationResponse update(String notificationId, UpdateNotificationRequest request, String actorId) {
        String id = requireText(notificationId, "notification id");
        NotificationResponse before = notifications.findByIdForUpdate(id)
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
        NotificationResponse after = notifications.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        List<String> changedFields = changedFields(request);
        if (!changedFields.isEmpty()) {
            audit.record(actorId, null, "NOTIFICATION_UPDATED", "NOTIFICATION", id,
                    "Notification " + id + " updated (fields: " + String.join(", ", changedFields) + ")",
                    notificationState(before),
                    notificationState(after));
        }
        return after;
    }

    private static List<String> changedFields(UpdateNotificationRequest request) {
        List<String> fields = new ArrayList<>();
        for (String field : List.of("userId", "title", "message", "type", "link")) {
            if (request.has(field)) {
                fields.add(field);
            }
        }
        return fields;
    }

    /**
     * Audit snapshot of an admin notification write. Deliberately excludes
     * title/message: those carry the notification body, which may contain
     * student-specific information and does not belong in the audit trail.
     * Ownership (userId) and type are what a reviewer needs to reconstruct
     * who the message was addressed to and how it was classified.
     */
    private static Map<String, Object> notificationState(NotificationResponse notification) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("userId", notification.userId());
        state.put("type", notification.type());
        return state;
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
