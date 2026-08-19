package io.campuscore.restfulapi.notification.web;

import java.time.Instant;
import java.util.List;

/**
 * Read-only notification DTOs kept separate from the legacy persistence model.
 *
 * <p>The envelope intentionally follows the Nest/Prisma response rather than
 * exposing Spring Data's {@code Page} shape. This module must remain a read
 * adapter until the legacy contract and database ownership are verified.</p>
 */
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
