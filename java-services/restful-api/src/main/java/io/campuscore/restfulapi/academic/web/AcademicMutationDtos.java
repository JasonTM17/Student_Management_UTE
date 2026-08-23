package io.campuscore.restfulapi.academic.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;

public final class AcademicMutationDtos {

    private AcademicMutationDtos() {
    }

    public record EnrollRequest(@NotBlank String sectionId) {
    }

    public record GradeUpdateRequest(@NotEmpty List<@Valid GradeUpdate> grades) {
    }

    public record GradeUpdate(
            @NotBlank String enrollmentId,
            @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal finalGrade,
            @NotBlank String letterGrade) {
    }
}
