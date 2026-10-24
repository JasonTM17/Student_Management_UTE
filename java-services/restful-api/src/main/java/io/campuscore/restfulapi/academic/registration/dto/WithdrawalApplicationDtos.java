package io.campuscore.restfulapi.academic.registration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class WithdrawalApplicationDtos {

    private WithdrawalApplicationDtos() {
    }

    public record CreateRequest(
            @NotBlank String enrollmentId,
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
            String enrollmentId,
            String semesterId,
            String semesterName,
            String courseCode,
            String courseName,
            String sectionNumber,
            String reason,
            String status,
            String reviewedBy,
            Instant reviewedAt,
            String reviewerNote,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** Withdrawal-window facts for one semester so the UI can gate the action. */
    public record WindowResponse(
            String semesterId,
            String semesterName,
            boolean regularSemester,
            boolean semesterOpen,
            Instant windowStart,
            Instant windowEnd,
            boolean open) {
    }
}
