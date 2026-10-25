package io.campuscore.restfulapi.engagement.model;

import java.time.Instant;
import java.util.List;

public record CreateAnnouncementCommand(
        String id,
        String title,
        String content,
        String priority,
        List<String> targetRoles,
        List<Integer> targetYears,
        List<String> targetDepartments,
        boolean isGlobal,
        Instant publishAt,
        Instant expiresAt,
        String publishedBy,
        String semesterId,
        String sectionId,
        String lecturerId,
        Instant createdAt) {
}
