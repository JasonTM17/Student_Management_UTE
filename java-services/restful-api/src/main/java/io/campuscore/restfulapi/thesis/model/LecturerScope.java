package io.campuscore.restfulapi.thesis.model;

/** A governance reader's live scope: their own department and its faculty. */
public record LecturerScope(String departmentId, String facultyId) { }
