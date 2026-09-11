package io.campuscore.restfulapi.academic.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTO models for UTE Student Conduct / Training Points ("Điểm rèn luyện" - DRL). */
public final class AcademicConductDtos {
    private AcademicConductDtos() {}

    public record ConductActivityDto(
            String id,
            String title,
            String category,
            BigDecimal points,
            LocalDate activityDate,
            String organizer,
            String certificateUrl
    ) {}

    public record ConductCriteriaScoreDto(
            String code,
            String nameVi,
            String nameEn,
            BigDecimal maxScore,
            BigDecimal score,
            String description
    ) {}

    public record ConductSemesterScoreDto(
            String id,
            String semesterId,
            String semesterName,
            BigDecimal criteria1Score,
            BigDecimal criteria2Score,
            BigDecimal criteria3Score,
            BigDecimal criteria4Score,
            BigDecimal criteria5Score,
            BigDecimal totalScore,
            String classification,
            String classificationVi,
            String status,
            String evaluatorName,
            List<ConductCriteriaScoreDto> criteria,
            List<ConductActivityDto> activities
    ) {}

    public record StudentConductSummaryDto(
            String studentId,
            String studentCode,
            String fullName,
            BigDecimal cumulativeAverageScore,
            String cumulativeClassificationVi,
            ConductSemesterScoreDto currentSemester,
            List<ConductSemesterScoreDto> history
    ) {}
}
