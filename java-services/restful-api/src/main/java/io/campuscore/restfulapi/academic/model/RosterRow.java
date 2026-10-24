package io.campuscore.restfulapi.academic.model;

public record RosterRow(
        String studentId,
        String studentNumber,
        String studentEmail,
        String studentFirstName,
        String studentLastName,
        String enrollmentStatus) {
}
