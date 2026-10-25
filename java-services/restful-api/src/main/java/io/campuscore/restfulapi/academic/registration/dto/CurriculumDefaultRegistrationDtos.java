package io.campuscore.restfulapi.academic.registration.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** API contracts for student curriculum plans and Academic Affairs batch runs. */
public final class CurriculumDefaultRegistrationDtos {
    private CurriculumDefaultRegistrationDtos() { }

    public enum CourseState {
        ALREADY_REGISTERED,
        REGISTERABLE,
        BLOCKED_PREREQUISITE,
        NO_OPEN_SECTION,
        FULL,
        CONFLICT,
        DISMISSED
    }

    public record TermResolution(String status, Integer curriculumYear, Integer curriculumSemester,
            String reasonCode) { }

    public record ScheduleOption(int dayOfWeek, String startTime, String endTime) { }

    public record SectionOption(String id, String sectionNumber, int capacity, int enrolledCount,
            int seatsAvailable, String status, boolean scheduleConflict, List<ScheduleOption> schedules) { }

    public record CoursePlan(String courseId, String courseCode, String name, String nameEn, String nameVi,
            int credits, CourseState state, String reasonCode, List<SectionOption> sections,
            SectionOption proposedSection) { }

    public record PlanResponse(String semesterId, String semesterName, String semesterNameEn,
            String semesterNameVi, String registrationRoundId, boolean registrationOpen,
            TermResolution term, List<CoursePlan> courses) { }

    public record CourseResult(String courseId, String courseCode, CourseState state, String reasonCode,
            String enrollmentId) { }

    public record StudentRegistrationResponse(String semesterId, int registeredCount, int skippedCount,
            List<CourseResult> courses) { }

    public record StudentRegistrationRequest(String semesterId, Integer curriculumYear, Integer curriculumSemester) { }

    public record AdminRunRequest(String roundId, String semesterId, Integer batchSize, String cursor) { }

    public record ReasonCount(String reasonCode, int count) { }

    public record AdminRunResponse(String runId, String roundId, String semesterId, boolean dryRun,
            int students, int courses, int wouldRegister, int registered, int skipped, int failedStudents,
            List<ReasonCount> skippedByReason, boolean hasMore, String nextCursor) { }

    public record AdminRunSummary(String id, String actorLabel, String summary, String afterState,
            Instant createdAt) { }

    public record AdminRunList(List<AdminRunSummary> runs) { }

    public static Map<String, Integer> countsByReason(List<ReasonCount> reasons) {
        return reasons.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                ReasonCount::reasonCode, ReasonCount::count));
    }
}
