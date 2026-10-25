package io.campuscore.restfulapi.academic.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class GradeAppealDtos {

    private GradeAppealDtos() {
    }

    public record CreateRequest(String enrollmentId, String reason) {
    }

    public record ReviewRequest(String decision, BigDecimal correctedFinalGrade, String reason) {
    }

    public record UnpublishRequest(List<String> enrollmentIds, String reason) {
    }

    public record Eligibility(
            String enrollmentId,
            String gradeStatus,
            Instant gradePublishedAt,
            Instant appealUntil,
            boolean appealAllowed) {
    }

    public record Response(
            String id,
            String enrollmentId,
            String studentId,
            String studentCode,
            String studentName,
            String courseCode,
            String courseName,
            String sectionId,
            String sectionNumber,
            String semesterName,
            BigDecimal finalGrade,
            String letterGrade,
            String gradeStatus,
            Instant gradePublishedAt,
            Instant appealUntil,
            String reason,
            String status,
            String resolutionReason,
            String reviewedBy,
            Instant reviewedAt,
            BigDecimal beforeFinalGrade,
            String beforeLetterGrade,
            BigDecimal afterFinalGrade,
            String afterLetterGrade,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record UnpublishResponse(String sectionId, int unpublishedCount) {
    }
}
