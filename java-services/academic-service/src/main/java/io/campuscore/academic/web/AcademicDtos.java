package io.campuscore.academic.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class AcademicDtos {

    private AcademicDtos() {
    }

    public record FacultyResponse(
            UUID id,
            String name,
            String nameEn,
            String nameVi,
            String code,
            String description,
            String descriptionEn,
            String descriptionVi,
            String dean,
            String phone,
            String email,
            String building,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreateFacultyRequest(
            @NotBlank String name,
            String nameEn,
            String nameVi,
            @NotBlank String code,
            String description,
            String descriptionEn,
            String descriptionVi,
            String dean,
            String phone,
            String email,
            String building) {
    }

    public record UpdateFacultyRequest(
            @NotBlank String name,
            String nameEn,
            String nameVi,
            @NotBlank String code,
            String description,
            String descriptionEn,
            String descriptionVi,
            String dean,
            String phone,
            String email,
            String building,
            boolean active) {
    }

    public record DepartmentResponse(
            UUID id,
            String name,
            String nameEn,
            String nameVi,
            String code,
            String description,
            String descriptionEn,
            String descriptionVi,
            String chair,
            String phone,
            String email,
            String building,
            UUID facultyId,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreateDepartmentRequest(
            @NotBlank String name,
            String nameEn,
            String nameVi,
            @NotBlank String code,
            String description,
            String descriptionEn,
            String descriptionVi,
            String chair,
            String phone,
            String email,
            String building,
            @NotNull UUID facultyId) {
    }

    public record UpdateDepartmentRequest(
            @NotBlank String name,
            String nameEn,
            String nameVi,
            @NotBlank String code,
            String description,
            String descriptionEn,
            String descriptionVi,
            String chair,
            String phone,
            String email,
            String building,
            @NotNull UUID facultyId,
            boolean active) {
    }

    public record CurriculumResponse(
            UUID id,
            String name,
            String nameEn,
            String nameVi,
            String code,
            UUID departmentId,
            String departmentCode,
            String departmentName,
            String description,
            int credits,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreateCurriculumRequest(
            @NotBlank String name,
            String nameEn,
            String nameVi,
            @NotBlank String code,
            @NotNull UUID departmentId,
            String departmentCode,
            String departmentName,
            String description,
            int credits) {
    }

    public record UpdateCurriculumRequest(
            @NotBlank String name,
            String nameEn,
            String nameVi,
            @NotBlank String code,
            @NotNull UUID departmentId,
            String departmentCode,
            String departmentName,
            String description,
            int credits,
            boolean active) {
    }

    public record CourseResponse(
            UUID id,
            String code,
            String name,
            String nameEn,
            String nameVi,
            String description,
            String descriptionEn,
            String descriptionVi,
            int credits,
            UUID departmentId,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreateCourseRequest(
            @NotBlank String code,
            @NotBlank String name,
            String nameEn,
            String nameVi,
            String description,
            String descriptionEn,
            String descriptionVi,
            int credits,
            @NotNull UUID departmentId) {
    }

    public record UpdateCourseRequest(
            @NotBlank String code,
            @NotBlank String name,
            String nameEn,
            String nameVi,
            String description,
            String descriptionEn,
            String descriptionVi,
            int credits,
            @NotNull UUID departmentId,
            boolean active) {
    }

    public record DeleteResponse(String message) {
    }
}
