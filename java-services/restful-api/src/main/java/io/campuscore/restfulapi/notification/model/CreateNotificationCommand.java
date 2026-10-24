package io.campuscore.restfulapi.notification.model;

public record CreateNotificationCommand(
        String id,
        String userId,
        String title,
        String message,
        String type,
        String link,
        java.time.Instant createdAt,
        String announcementId) {

    /** Non-announcement notices carry no source link. */
    public CreateNotificationCommand(
            String id,
            String userId,
            String title,
            String message,
            String type,
            String link,
            java.time.Instant createdAt) {
        this(id, userId, title, message, type, link, createdAt, null);
    }
}
