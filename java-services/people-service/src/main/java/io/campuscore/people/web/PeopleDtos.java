package io.campuscore.people.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class PeopleDtos {

    private PeopleDtos() {
    }

    public record CreateStudentRequest(
            @NotNull UUID userId,
            @NotBlank @Email String email,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String studentId,
            @NotNull UUID curriculumId,
            String curriculumCode,
            String curriculumName,
            UUID departmentId,
            String departmentCode,
            String departmentName,
            int year,
            String status,
            @NotNull Instant admissionDate) {
    }

    public record UpdateStudentRequest(
            @NotNull UUID userId,
            @NotBlank @Email String email,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String studentId,
            @NotNull UUID curriculumId,
            String curriculumCode,
            String curriculumName,
            UUID departmentId,
            String departmentCode,
            String departmentName,
            int year,
            String status,
            @NotNull Instant admissionDate) {
    }

    public record StudentResponse(
            UUID id,
            UUID userId,
            String email,
            String firstName,
            String lastName,
            String studentId,
            UUID curriculumId,
            String curriculumCode,
            String curriculumName,
            UUID departmentId,
            String departmentCode,
            String departmentName,
            int year,
            String status,
            Instant admissionDate,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreateLecturerRequest(
            @NotNull UUID userId,
            @NotBlank @Email String email,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotNull UUID departmentId,
            String departmentCode,
            String departmentName,
            @NotBlank String employeeId,
            String title,
            String specialization,
            String office,
            String phone,
            Boolean active) {
    }

    public record UpdateLecturerRequest(
            @NotNull UUID userId,
            @NotBlank @Email String email,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotNull UUID departmentId,
            String departmentCode,
            String departmentName,
            @NotBlank String employeeId,
            String title,
            String specialization,
            String office,
            String phone,
            Boolean active) {
    }

    public record LecturerResponse(
            UUID id,
            UUID userId,
            String email,
            String firstName,
            String lastName,
            UUID departmentId,
            String departmentCode,
            String departmentName,
            String employeeId,
            String title,
            String specialization,
            String office,
            String phone,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record DeleteResponse(String message) {
    }
}
