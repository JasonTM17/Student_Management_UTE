package io.campuscore.notification.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record CreateNotificationRequest(
            @NotNull UUID userId,
            @NotBlank @Size(max = 200) String title,
            @NotBlank String message,
            @NotBlank @Size(max = 40) String type,
            String link) {
    }

    public record NotificationResponse(
            UUID id,
            UUID userId,
            String title,
            String message,
            String type,
            String link,
            boolean read,
            Instant readAt,
            Instant createdAt) {
    }

    public record UnreadCountResponse(long unreadCount) {
    }

    public record MarkReadResponse(UUID id, boolean read, Instant readAt) {
    }

    public record MarkAllReadResponse(long updated) {
    }

    public record DeleteResponse(String message) {
    }
}
