package io.campuscore.restfulapi.academic.registration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class CreditLimitApplicationDtos {

    private CreditLimitApplicationDtos() {
    }

    public record CreateRequest(
            @NotBlank String roundId,
            @NotBlank @Size(min = 20, max = 1000) String reason) {
    }

    public record ReviewRequest(
            @NotBlank String decision,
            @Size(max = 1000) String note) {
    }

    public record Response(
            String id,
            String studentId,
            String studentCode,
            String studentName,
            String studentEmail,
            String semesterId,
            String semesterName,
            String roundId,
            String roundName,
            int standardLimit,
            int requestedLimit,
            String reason,
            String status,
            String reviewedBy,
            Instant reviewedAt,
            String reviewerNote,
            Instant createdAt,
            Instant updatedAt) {
    }
}
