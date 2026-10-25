package io.campuscore.restfulapi.engagement.model;

import java.time.Instant;
import java.util.List;

public record UpdateAnnouncementCommand(
        String id,
        PatchValue<String> title,
        PatchValue<String> content,
        PatchValue<String> priority,
        PatchValue<List<String>> targetRoles,
        PatchValue<List<Integer>> targetYears,
        PatchValue<List<String>> targetDepartments,
        PatchValue<Boolean> isGlobal,
        PatchValue<Instant> publishAt,
        PatchValue<Instant> expiresAt,
        PatchValue<String> semesterId,
        PatchValue<String> sectionId,
        PatchValue<String> lecturerId,
        Instant updatedAt,
        int expectedVersion) {
}
