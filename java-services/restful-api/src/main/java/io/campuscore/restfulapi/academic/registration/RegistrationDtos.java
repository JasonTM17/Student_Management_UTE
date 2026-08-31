package io.campuscore.restfulapi.academic.registration;

import java.time.Instant;
import java.util.List;

public final class RegistrationDtos {

    private RegistrationDtos() {
    }

    public record RoundResponse(
            String id,
            String semesterId,
            String name,
            String kind,
            String status,
            Instant windowStart,
            Instant windowEnd,
            int creditLimit) {
    }

    public record EligibilityResponse(
            String roundId,
            String semesterId,
            String kind,
            boolean eligible,
            int creditLimit,
            int creditsUsed,
            int creditsRemaining,
            Instant windowStart,
            Instant windowEnd) {
    }

    public record CatalogSectionResponse(
            String id,
            String sectionNumber,
            String courseId,
            String courseCode,
            String courseName,
            int credits,
            int capacity,
            int enrolledCount,
            int remainingSeats,
            String status,
            boolean scheduleConflict,
            boolean alreadyEnrolled) {
    }

    public record SummaryResponse(
            String roundId,
            int creditLimit,
            int creditsUsed,
            int creditsRemaining,
            List<String> enrollmentIds) {
    }
}
