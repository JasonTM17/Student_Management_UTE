package io.campuscore.restfulapi.thesis.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Per-topic score transparency read model: which lecturers graded, the role
 * each graded under, the submitted score and comment, the finalize-arithmetic
 * average, and which eligible graders have not submitted yet.
 *
 * <p>Two views share this shape. Governance staff, the topic's council
 * members, the counter-reviewer, and (after publication) the supervisors see
 * every column. A participant student of the bound approved group sees only
 * the published outcome — the per-grader score, comment, scoredAt, and
 * countedInAverage fields are nulled and pendingGraders plus the finalizer
 * identity are stripped — so the row carries the average, grader count, and
 * the graders' names and roles only.
 */
public record ScoreBreakdownResponse(
        UUID topicId,
        UUID councilId,
        String councilName,
        BigDecimal average,
        int graderCount,
        boolean complete,
        BigDecimal finalScore,
        String finalizedBy,
        Instant finalizedAt,
        String resultStatus,
        List<ScoreBreakdownGrader> graders,
        List<ScoreBreakdownPendingGrader> pendingGraders) {

    /**
     * One submitted score with the grader's seat role. {@code role} is
     * CHAIR, SECRETARY or MEMBER for council defense scores and
     * GVPB_REVIEWER for the counter-review row. {@code countedInAverage}
     * marks the rows the finalize arithmetic would include; scores left
     * behind by a member who later became ineligible stay visible but
     * excluded, mirroring the finalize filter.
     */
    public record ScoreBreakdownGrader(
            String lecturerId,
            String displayName,
            String role,
            BigDecimal score,
            String comment,
            Instant scoredAt,
            Boolean countedInAverage) { }

    /** An eligible grader who has not submitted yet, named for the chair. */
    public record ScoreBreakdownPendingGrader(
            String lecturerId,
            String displayName,
            String role) { }
}
