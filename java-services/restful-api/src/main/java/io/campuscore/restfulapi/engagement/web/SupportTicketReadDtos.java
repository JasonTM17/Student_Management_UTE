package io.campuscore.restfulapi.engagement.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

/** Legacy-compatible response records for the read-only support-ticket candidate. */
public final class SupportTicketReadDtos {

    private SupportTicketReadDtos() {
    }

    public record SupportTicketResponse(
            String id,
            String ticketNumber,
            String userId,
            String userEmail,
            String userDisplayName,
            String subject,
            String description,
            String category,
            String priority,
            String status,
            String assignedTo,
            String assignedToDisplayName,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant resolvedAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant closedAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            TicketUser user,
            List<TicketResponse> responses) {
    }

    public record TicketResponse(
            String id,
            String ticketId,
            String userId,
            String userEmail,
            String userDisplayName,
            String message,
            boolean isInternal,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            TicketUser user) {
    }

    public record TicketUser(String id, String email, String displayName) {
    }

    public record PageMeta(long total, int page, int limit, int totalPages) {
    }

    public record SupportTicketListResponse(List<SupportTicketResponse> data, PageMeta meta) {
    }
}
