package io.campuscore.restfulapi.notification.model;

public record UpdateNotificationCommand(
        String id,
        PatchValue<String> userId,
        PatchValue<String> title,
        PatchValue<String> message,
        PatchValue<String> type,
        PatchValue<String> link) {
}
