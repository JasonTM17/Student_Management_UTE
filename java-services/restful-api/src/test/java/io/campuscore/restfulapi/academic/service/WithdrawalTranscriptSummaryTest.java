package io.campuscore.restfulapi.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.campuscore.restfulapi.academic.dto.AcademicEnrollmentReadDtos.GradeSummary;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression: an approved withdrawal renders as W on the transcript but must
 * count nowhere — not attempted, not earned, not GPA, never the best attempt.
 */
class WithdrawalTranscriptSummaryTest {

    private final AcademicEnrollmentReadService service = new AcademicEnrollmentReadService(null);

    private static GradeSummary grade(
            String id, String course, int credits, String semesterId, String letter, String status) {
        return new GradeSummary(id, course, "Course " + course, null, null, credits,
                "01", "Lecturer", "Sem", null, null, semesterId,
                null, null, null, letter, "PUBLISHED", status);
    }

    @Test
    void withdrawnAttemptShowsOnTranscriptButCountsNowhere() {
        var transcript = service.findStudentTranscript("student-1", List.of(
                grade("e1", "SE101", 3, "s1", "A", "COMPLETED"),
                grade("e2", "SE102", 4, "s1", "W", "WITHDRAWN")));

        assertThat(transcript.semesters()).hasSize(1);
        var semester = transcript.semesters().get(0);
        assertThat(semester.records()).extracting(GradeSummary::letterGrade).contains("W");
        assertThat(semester.creditsAttempted()).isEqualTo(3);
        assertThat(semester.creditsEarned()).isEqualTo(3);
        assertThat(transcript.summary().totalCreditsAttempted()).isEqualTo(3);
        assertThat(transcript.summary().totalCreditsEarned()).isEqualTo(3);
        assertThat(transcript.summary().cumulativeGpa())
                .isEqualByComparingTo(new BigDecimal("4.00"));
    }

    @Test
    void withdrawnAttemptNeverDisplacesAGradedBestAttempt() {
        var transcript = service.findStudentTranscript("student-1", List.of(
                grade("e1", "SE101", 3, "s1", "W", "WITHDRAWN"),
                grade("e2", "SE101", 3, "s2", "F", "COMPLETED")));

        // The real F attempt is still counted; the W adds nothing.
        assertThat(transcript.summary().totalCreditsAttempted()).isEqualTo(3);
        assertThat(transcript.summary().totalCreditsEarned()).isEqualTo(0);
    }
}
