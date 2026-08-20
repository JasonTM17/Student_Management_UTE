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
}
