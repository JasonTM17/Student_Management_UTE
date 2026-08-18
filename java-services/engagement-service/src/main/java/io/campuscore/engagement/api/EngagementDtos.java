package io.campuscore.engagement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EngagementDtos {

    private EngagementDtos() {
    }

    public record CreateAnnouncementRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank String description,
            String priority,
            List<String> targetRoles,
            List<Integer> targetYears,
            Boolean isGlobal,
            Instant publishAt,
            Instant expiresAt,
            UUID semesterId,
            UUID sectionId) {
    }

    public record AnnouncementResponse(
            UUID id,
            String title,
            String description,
            String priority,
            List<String> targetRoles,
            List<Integer> targetYears,
            boolean isGlobal,
            Instant publishAt,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreateTicketRequest(
            @NotBlank @Size(max = 200) String subject,
            @NotBlank String description,
            @NotBlank String category,
            String priority) {
    }

    public record TicketResponse(
            UUID id,
            String ticketNumber,
            UUID userId,
            String userEmail,
            String userDisplayName,
            String subject,
            String description,
            String category,
            String priority,
            String status,
            UUID assignedTo,
            String assignedToDisplayName,
            Instant resolvedAt,
            Instant closedAt,
            Instant createdAt) {
    }

    public record CreateTicketResponseRequest(
            @NotBlank String message,
            Boolean isInternal) {
    }

    public record TicketResponseView(
            UUID id,
            UUID ticketId,
            UUID userId,
            String userEmail,
            String userDisplayName,
            String message,
            boolean isInternal,
            Instant createdAt) {
    }

    public record AssignTicketRequest(
            @NotNull UUID assignedTo) {
    }
}
