package io.campuscore.restfulapi.academic.web;

import java.util.List;

/** DTOs for academic schedule reads. */
public final class AcademicScheduleReadDtos {
    private AcademicScheduleReadDtos() {
    }

    public record PageMeta(long total, int page, int limit, int totalPages) {
    }

    public record ClassroomResponse(String id, String building, String roomNumber) {
    }

    public record SectionResponse(
            String id,
            String sectionNumber,
            String courseId,
            String semesterId,
            String lecturerId,
            String classroomId,
            int capacity,
            int enrolledCount,
            String status) {
    }

    public record ScheduleResponse(
            String id,
            String sectionId,
            String classroomId,
            int dayOfWeek,
            String startTime,
            String endTime,
            SectionResponse section,
            ClassroomResponse classroom) {
    }

    public record ScheduleListResponse(List<ScheduleResponse> data, PageMeta meta) {
    }
}
