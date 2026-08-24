package io.campuscore.restfulapi.registration;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/** Public DTOs for the student registration workspace. Entities never cross this boundary. */
public final class RegistrationDtos {
    private RegistrationDtos() { }

    public record RoundView(String id, String semesterId, String status, Instant registrationStart,
            Instant registrationEnd, Instant addDropStart, Instant addDropEnd, Instant serverNow,
            String institutionTimeZone, int maxCredits, long version) { }

    public record RoundPage(List<RoundView> items, String nextCursor) { }

    public record ScheduleView(String id, int dayOfWeek, LocalTime startTime, LocalTime endTime,
            String classroomId, String building, String roomNumber) { }

    public record SectionView(String id, String courseId, String courseCode, String courseName,
            int credits, String sectionNumber, String lecturerName, String classroom,
            int capacity, int enrolledCount, int remainingSeats, String status,
            boolean selected, List<ScheduleView> schedules, List<String> violations) { }

    public record EligibilityView(String roundId, String eligibilityState, Integer priorityRank,
            int maxCredits, int selectedCredits, String reasonCode, Instant serverNow) { }

    public record SummaryView(String roundId, int selectedCredits, int maxCredits,
            int selectedCount, List<SectionView> selectedSections) { }

    public record EnrollmentView(String id, String sectionId, String roundId, String status,
            Instant enrolledAt, SectionView section) { }

    public record EnrollmentPage(List<EnrollmentView> items, String nextCursor) { }

    public record EnrollmentRequest(@NotBlank String sectionId, @NotBlank String roundId) { }

    public record ValidationRequest(@NotBlank String sectionId, @NotBlank String roundId) { }

    public record ValidationResponse(boolean valid, List<String> violations) { }

    public record MutationResponse(EnrollmentView enrollment, boolean replayed,
            String clientRequestId) { }
}
