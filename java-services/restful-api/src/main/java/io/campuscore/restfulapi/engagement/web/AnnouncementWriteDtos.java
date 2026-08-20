package io.campuscore.restfulapi.engagement.web;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** Request records for feature-gated announcement write candidates. */
public final class AnnouncementWriteDtos {

    private AnnouncementWriteDtos() {
    }

    public record CreateAnnouncementRequest(
            @NotNull String title,
            @NotNull String content,
            String priority,
            List<?> targetRoles,
            List<?> targetYears,
            Boolean isGlobal,
            Instant publishAt,
            Instant expiresAt,
            String semesterId,
            String sectionId,
            String lecturerId) {
    }
}
