package io.campuscore.restfulapi.thesis.dto;

/** `firstName`/`lastName`/`email` are null when the lecturer has no directory row. */
public record SupervisorRow(
        String lecturerId,
        int supervisorOrder,
        String firstName,
        String lastName,
        String email) { }
