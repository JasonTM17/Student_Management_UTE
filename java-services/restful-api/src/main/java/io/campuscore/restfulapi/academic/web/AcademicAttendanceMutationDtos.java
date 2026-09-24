package io.campuscore.restfulapi.academic.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** DTOs for academic attendance mutations. */
public final class AcademicAttendanceMutationDtos {
    private AcademicAttendanceMutationDtos() {
    }

    public record StudentAttendanceEntry(
            @NotBlank(message = "studentId is required")
            String studentId,

            @NotBlank(message = "status is required")
            String status,

            @Size(max = 1000, message = "notes must not exceed 1000 characters")
            String notes
    ) {
    }

    public record SectionAttendanceUpdateRequest(
            @NotBlank(message = "date is required")
            String date,

            @NotNull(message = "records list is required")
            @Valid
            List<StudentAttendanceEntry> records
    ) {
    }

    public record AttendanceMutationResponse(
            String sectionId,
            String date,
            int updatedCount,
            String message
    ) {
    }
}
