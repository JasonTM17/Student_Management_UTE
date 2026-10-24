package io.campuscore.restfulapi.academic.registration.model;

import java.time.Instant;
import java.util.List;

/** Repository row projections for curriculum default registration. */
public final class CurriculumDefaultRegistrationRows {
    private CurriculumDefaultRegistrationRows() { }

    public record StudentRow(String id, String curriculumId, Integer year, Instant admissionDate, String status) { }

    public record AcademicYearRow(String id, int year, Instant startDate, Instant endDate) { }

    public record SemesterRow(String id, String name, String nameEn, String nameVi, String type,
            int academicYear, Instant startDate, Instant endDate, String status) { }

    public record RoundRow(String id, String semesterId, String kind, String status,
            Instant windowStart, Instant windowEnd, int creditLimit) { }

    public record CourseRow(String id, String code, String name, String nameEn, String nameVi, int credits) { }

    public record ScheduleRow(int dayOfWeek, String startTime, String endTime) { }

    public record SectionRow(String id, String sectionNumber, int capacity, int enrolledCount,
            String status, List<ScheduleRow> schedules) { }

    public record EnrollmentState(String courseId, String status, String registrationSource) { }

    public record StudentCursorRow(String id, String curriculumId, Integer year, Instant admissionDate) { }

    public record RegistrationAvailability(boolean eligible, String reasonCode, int creditLimit,
            int creditsUsed, int creditsRemaining) { }

    public record AdminAuditRow(String id, String actorLabel, String summary, String afterState, Instant createdAt) { }
}
