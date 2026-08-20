package io.campuscore.restfulapi.notification.web;

/** Disabled-by-default notification write DTOs for the strangler candidate. */
public final class NotificationWriteDtos {

    private NotificationWriteDtos() {
    }

    public record MarkAllReadResponse(int updated) {
    }

    public record DeleteNotificationResponse(String message) {
    }
}
