package io.campuscore.notification.service;

import io.campuscore.notification.domain.Notification;
import io.campuscore.notification.repository.NotificationRepository;
import io.campuscore.notification.web.NotificationDtos;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public Page<NotificationDtos.NotificationResponse> findMy(UUID userId, int page, int limit, Boolean isRead) {
        return notifications.findAllByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100)))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public NotificationDtos.UnreadCountResponse getUnreadCount(UUID userId) {
        return new NotificationDtos.UnreadCountResponse(notifications.countByUserIdAndReadFalse(userId));
    }

    @Transactional
    public NotificationDtos.MarkReadResponse markRead(UUID userId, UUID id) {
        Notification notification = notifications.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (!notification.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Cannot access this notification");
        }
        notification.markRead();
        Notification saved = notifications.save(notification);
        return new NotificationDtos.MarkReadResponse(saved.getId(), true, saved.getReadAt());
    }

    @Transactional
    public NotificationDtos.MarkAllReadResponse markAllRead(UUID userId) {
        int updated = notifications.markAllAsRead(userId);
        return new NotificationDtos.MarkAllReadResponse(updated);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        if (!notifications.existsByIdAndUserId(id, userId)) {
            throw new IllegalArgumentException("Cannot delete this notification");
        }
        notifications.deleteById(id);
    }

    @Transactional
    public NotificationDtos.NotificationResponse create(NotificationDtos.CreateNotificationRequest request) {
        Notification notification = new Notification(request.userId(), request.title(), request.message(), request.type(), request.link());
        return toResponse(notifications.save(notification));
    }

    @Transactional(readOnly = true)
    public Page<NotificationDtos.NotificationResponse> findAll(int page, int limit, UUID userId) {
        return notifications.findAll(PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100)))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public NotificationDtos.NotificationResponse findOne(UUID id) {
        return toResponse(notifications.findById(id).orElseThrow(() -> new IllegalArgumentException("Notification not found")));
    }

    @Transactional
    public NotificationDtos.NotificationResponse update(UUID id, NotificationDtos.CreateNotificationRequest request) {
        Notification notification = notifications.findById(id).orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        return toResponse(notifications.save(notification));
    }

    @Transactional
    public void remove(UUID id) {
        notifications.deleteById(id);
    }

    private NotificationDtos.NotificationResponse toResponse(Notification notification) {
        return new NotificationDtos.NotificationResponse(
                notification.getId(),
                notification.getUserId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType(),
                notification.getLink(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
