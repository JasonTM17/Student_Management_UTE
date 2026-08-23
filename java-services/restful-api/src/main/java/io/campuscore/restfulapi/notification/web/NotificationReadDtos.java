package io.campuscore.restfulapi.notification.web;

import java.time.Instant;
import java.util.List;

/** Stable notification DTOs that avoid exposing persistence implementation details. */
public final class NotificationReadDtos {

    private NotificationReadDtos() {
    }

    public record NotificationResponse(
            String id,
            String userId,
            String title,
            String message,
            String type,
            String link,
            boolean isRead,
            Instant readAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record PageMeta(
            long total,
            int page,
            int limit,
            int totalPages) {
    }

    public record NotificationListResponse(
            List<NotificationResponse> data,
            PageMeta meta) {
    }

    public record UnreadCountResponse(long unreadCount) {
    }
}
