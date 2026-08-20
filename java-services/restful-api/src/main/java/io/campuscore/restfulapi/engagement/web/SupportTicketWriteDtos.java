package io.campuscore.restfulapi.engagement.web;

import jakarta.validation.constraints.NotNull;

/** Request records for feature-gated support-ticket write candidates. */
public final class SupportTicketWriteDtos {

    private SupportTicketWriteDtos() {
    }

    public record CreateSupportTicketRequest(
            @NotNull String subject,
            @NotNull String description,
            @NotNull String category,
            String priority) {
    }

    public record CreateTicketResponseRequest(
            @NotNull String message,
            Boolean isInternal) {
    }

    public record UpdateSupportTicketRequest(
            String subject,
            String description,
            String category,
            String priority,
            String status) {
    }

    public record AssignSupportTicketRequest(
            @NotNull String assignedTo) {
    }
}
