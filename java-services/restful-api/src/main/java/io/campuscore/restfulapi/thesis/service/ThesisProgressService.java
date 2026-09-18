package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.web.ThesisProgressDtos.ProgressResponse;
import io.campuscore.restfulapi.web.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derives personal thesis progress from persisted evidence. Round status is
 * never allowed to stand in for a student's group, report, council or result.
 */
@Service
@Profile("persistence")
public class ThesisProgressService {

    private static final List<String> MILESTONES = List.of(
            "ROUND_SELECTED",
            "GROUP_CREATED",
            "TOPIC_ASSIGNED",
            "GROUP_APPROVED",
            "REPORT_SUBMITTED",
            "COUNCIL_ASSIGNED",
            "SCORE_FINALIZED",
            "RESULTS_PUBLISHED");

    private final NamedParameterJdbcTemplate jdbc;

    public ThesisProgressService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ProgressResponse get(UUID roundId, Jwt actor) {
        if (roundId == null) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "roundId is required");
        }
        String studentId = actor == null ? "" : normalize(actor.getClaimAsString("studentId"));
        if (studentId.isBlank()) {
            throw new DomainException(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED",
                    "An active student profile is required to read personal thesis progress");
        }

        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("""
                    SELECT r.status AS round_status,
                           g.id AS group_id,
                           g.status AS group_status,
                           g.approval_status,
                           t.id AS topic_id,
                           t.title AS topic_title,
                           rep.id AS report_id,
                           rep.submitted_at AS report_submitted_at,
                           ct.council_id,
                           t.final_score,
                           t.final_score_finalized_at,
                           t.result_status,
                           (SELECT COUNT(*) FROM thesis.thesis_group_member gm WHERE gm.group_id = g.id) AS member_count
                    FROM thesis.thesis_registration_round r
                    LEFT JOIN thesis.thesis_group g
                      ON g.round_id = r.id
                     AND EXISTS (
                         SELECT 1 FROM thesis.thesis_group_member mine
                         WHERE mine.group_id = g.id AND mine.student_id = :studentId
                     )
                    LEFT JOIN thesis.thesis_topic t ON t.id = g.topic_id
                    LEFT JOIN thesis.thesis_group_report rep ON rep.group_id = g.id
                    LEFT JOIN thesis.thesis_council_topic ct ON ct.topic_id = t.id
                    WHERE r.id = :roundId
                    ORDER BY g.created_at DESC
                    LIMIT 1
                    """, params().addValue("roundId", roundId).addValue("studentId", studentId));
        } catch (EmptyResultDataAccessException exception) {
            throw new DomainException(HttpStatus.NOT_FOUND, "ROUND_NOT_FOUND",
                    "Thesis registration round not found");
        }

        String roundStatus = string(row.get("round_status"));
        UUID groupId = uuid(row.get("group_id"));
        String groupStatus = string(row.get("group_status"));
        String approvalStatus = string(row.get("approval_status"));
        UUID topicId = uuid(row.get("topic_id"));
        UUID reportId = uuid(row.get("report_id"));
        UUID councilId = uuid(row.get("council_id"));
        BigDecimal finalScore = (BigDecimal) row.get("final_score");
        Instant finalScoreFinalizedAt = instantOf(row.get("final_score_finalized_at"));
        int memberCount = number(row.get("member_count"));

        /*
         * Milestones form a causal chain.  Later rows are useful evidence, but
         * they must never make an earlier missing prerequisite look complete
         * (for example a retained report after V63 re-opened a group).  Keep
         * the published projection monotonic and separately flag the broken
         * chain for operators.
         */
        boolean approvedGroup = "APPROVED".equals(approvalStatus)
                && memberCount >= 3 && memberCount <= 4;
        boolean scoreFinalized = finalScore != null
                && "GRADED".equals(string(row.get("result_status")));
        boolean publishedResult = "RESULTS_PUBLISHED".equals(roundStatus)
                && approvedGroup
                && scoreFinalized;
        List<Boolean> evidence = List.of(
                groupId != null,
                groupId != null,
                topicId != null,
                approvedGroup,
                reportId != null,
                councilId != null,
                scoreFinalized,
                publishedResult);
        List<String> completed = new ArrayList<>();
        boolean chainOpen = true;
        for (int index = 0; index < MILESTONES.size(); index++) {
            if (!chainOpen || !evidence.get(index)) {
                chainOpen = false;
                continue;
            }
            completed.add(MILESTONES.get(index));
        }
        boolean inconsistentLaterEvidence = false;
        int firstMissing = completed.size();
        for (int index = firstMissing + 1; index < evidence.size(); index++) {
            if (evidence.get(index)) {
                inconsistentLaterEvidence = true;
                break;
            }
        }

        String attention = "NONE";
        if (groupId == null) {
            attention = "NOT_PARTICIPATING";
        } else if ("REJECTED".equals(approvalStatus)) {
            attention = "GROUP_REJECTED";
        } else if ("CANCELLED".equals(groupStatus)) {
            attention = "GROUP_CANCELLED";
        } else if (memberCount < 3 || memberCount > 4) {
            attention = "GROUP_INVALID_MEMBER_COUNT";
        } else if (inconsistentLaterEvidence) {
            attention = "PROGRESS_INCONSISTENT";
        } else if ("RESULTS_PUBLISHED".equals(roundStatus) && finalScore == null) {
            attention = "RESULT_NOT_AVAILABLE";
        }

        String current = completed.isEmpty() ? "ROUND_SELECTED" : completed.get(completed.size() - 1);
        if ("RESULT_NOT_AVAILABLE".equals(attention) || "GROUP_INVALID_MEMBER_COUNT".equals(attention)) {
            current = completed.get(completed.size() - 1);
        }
        return new ProgressResponse(
                roundId,
                roundStatus,
                groupId == null ? "NOT_PARTICIPATING" : "PARTICIPATING",
                current,
                List.copyOf(completed),
                attention,
                groupId,
                groupStatus,
                approvalStatus,
                memberCount,
                topicId,
                string(row.get("topic_title")),
                reportId,
                instantOf(row.get("report_submitted_at")),
                councilId,
                finalScore,
                finalScoreFinalizedAt,
                groupStatus);
    }

    private static MapSqlParameterSource params() {
        return new MapSqlParameterSource();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static UUID uuid(Object value) {
        if (value == null) return null;
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private static Instant instantOf(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        return null;
    }
}
