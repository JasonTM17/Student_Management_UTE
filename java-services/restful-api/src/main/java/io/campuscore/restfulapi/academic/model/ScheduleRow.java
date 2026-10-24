package io.campuscore.restfulapi.academic.model;

public record ScheduleRow(String id, String sectionId, int dayOfWeek, String startTime, String endTime,
        String classroomId, String building, String roomNumber) {
}
