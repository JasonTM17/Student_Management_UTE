package io.campuscore.restfulapi.thesis.repository;

import static io.campuscore.restfulapi.thesis.repository.ThesisMutationSql.one;
import static io.campuscore.restfulapi.thesis.repository.ThesisMutationSql.params;

import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.PublishResultsExcludedTopic;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Write-side statements for the results publication lifecycle: the preview's
 * completeness aggregates, the guarded publish transition support reads, and
 * the retract/close/reopen status flips. The included/excluded predicates here
 * must stay byte-identical to each other — publishResults and its preview
 * spell "included" the same way on purpose.
 */
@Repository
@Profile("persistence")
public class ThesisPublicationRepository {

    private static final String ROUND_NOT_FOUND_MESSAGE = "Thesis registration round not found";

    private final NamedParameterJdbcTemplate jdbc;

    public ThesisPublicationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void requireExisting(UUID id) {
        one(jdbc, "SELECT id FROM thesis.thesis_registration_round WHERE id = :id",
                params().addValue("id", id), "ROUND_NOT_FOUND", ROUND_NOT_FOUND_MESSAGE);
    }

    /**
     * Included/ungraded aggregates over the round's approved groups. A
     * sub-threshold counter-review while its window is still open is
     * provisional — the reviewer may overwrite — so it neither excludes the
     * topic nor blocks anything. A NULL result_status makes the exclusion
     * condition NULL, and NOT NULL is still NULL — so "included" is spelled
     * IS NULL OR NOT &lt;cond&gt;.
     */
    public Map<String, Object> countScoringStates(UUID roundId) {
        return jdbc.queryForMap(
                "SELECT COUNT(*) FILTER (WHERE s.result_status IS NULL OR NOT s.excluded_condition) AS included_count, "
                        + "COUNT(*) FILTER (WHERE s.final_score IS NULL AND (s.result_status IS NULL OR NOT s.excluded_condition)) AS ungraded_count "
                        + "FROM (SELECT t.final_score, t.result_status, "
                        + "  (t.result_status = 'WITHDRAWN' OR (t.result_status = 'REVIEW_FAILED' "
                        + "    AND r.gvpb_deadline IS NOT NULL AND r.gvpb_deadline <= CURRENT_TIMESTAMP)) "
                        + "  AS excluded_condition "
                        + "  FROM thesis.thesis_group g "
                        + "  JOIN thesis.thesis_topic t ON t.id = g.topic_id "
                        + "  JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "  WHERE g.round_id = :roundId AND g.approval_status = 'APPROVED') s",
                params().addValue("roundId", roundId));
    }

    /**
     * Definitively excluded topics of a round (WITHDRAWN, or REVIEW_FAILED
     * whose window closed), optionally narrowed to one faculty for a faculty
     * head's preview.
     */
    public List<PublishResultsExcludedTopic> listExcludedTopics(UUID roundId, String facultyId) {
        String facultyScope = facultyId == null ? "" : " AND d.\"facultyId\" = :facultyId";
        return jdbc.query(
                "SELECT t.id, t.title, t.result_status, t.result_reason FROM thesis.thesis_topic t "
                        + "JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "JOIN academic.\"Department\" d ON d.\"id\" = t.department_id "
                        + "WHERE t.round_id = :roundId "
                        + "AND (t.result_status = 'WITHDRAWN' OR (t.result_status = 'REVIEW_FAILED' "
                        + "  AND r.gvpb_deadline IS NOT NULL AND r.gvpb_deadline <= CURRENT_TIMESTAMP)) "
                        + facultyScope
                        + " ORDER BY t.title",
                facultyId == null
                        ? params().addValue("roundId", roundId)
                        : params().addValue("roundId", roundId).addValue("facultyId", facultyId),
                (rs, ignored) -> new PublishResultsExcludedTopic(
                        (UUID) rs.getObject("id"), rs.getString("title"),
                        rs.getString("result_status"), rs.getString("result_reason")));
    }

    /** Provisionally failed topics whose counter-review window is still open. */
    public List<PublishResultsExcludedTopic> listOpenReviewTopics(UUID roundId) {
        return jdbc.query(
                "SELECT t.id, t.title, t.result_status, t.result_reason FROM thesis.thesis_topic t "
                        + "JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "WHERE t.round_id = :roundId AND t.result_status = 'REVIEW_FAILED' "
                        + "AND (r.gvpb_deadline IS NULL OR r.gvpb_deadline > CURRENT_TIMESTAMP) "
                        + "ORDER BY t.title",
                params().addValue("roundId", roundId),
                (rs, ignored) -> new PublishResultsExcludedTopic(
                        (UUID) rs.getObject("id"), rs.getString("title"),
                        rs.getString("result_status"), rs.getString("result_reason")));
    }

    /** Locks the round row so a concurrent approve cannot slip past the completeness check. */
    public void lockRoundRow(UUID id) {
        jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), UUID.class);
    }

    public Integer countApprovedGroups(UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group WHERE round_id = :id AND approval_status = 'APPROVED'",
                params().addValue("id", id), Integer.class);
    }

    public List<String> listOpenReviewIdTitles(UUID id) {
        return jdbc.queryForList(
                "SELECT CAST(t.id AS VARCHAR) || ' ' || t.title FROM thesis.thesis_topic t "
                        + "JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "WHERE t.round_id = :id AND t.result_status = 'REVIEW_FAILED' "
                        + "AND (r.gvpb_deadline IS NULL OR r.gvpb_deadline > CURRENT_TIMESTAMP) "
                        + "ORDER BY t.id",
                params().addValue("id", id), String.class);
    }

    public Integer countIncludedGroups(UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group g JOIN thesis.thesis_topic t ON t.id = g.topic_id "
                        + "JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "WHERE g.round_id = :id AND g.approval_status = 'APPROVED' "
                        + "AND (t.result_status IS NULL OR NOT (t.result_status = 'WITHDRAWN' "
                        + "OR (t.result_status = 'REVIEW_FAILED' "
                        + "AND r.gvpb_deadline IS NOT NULL AND r.gvpb_deadline <= CURRENT_TIMESTAMP)))",
                params().addValue("id", id), Integer.class);
    }

    public Integer countUngradedGroups(UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group g JOIN thesis.thesis_topic t ON t.id = g.topic_id "
                        + "JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "WHERE g.round_id = :id AND g.approval_status = 'APPROVED' "
                        + "AND t.final_score IS NULL "
                        + "AND (t.result_status IS NULL OR NOT (t.result_status = 'WITHDRAWN' "
                        + "OR (t.result_status = 'REVIEW_FAILED' "
                        + "AND r.gvpb_deadline IS NOT NULL AND r.gvpb_deadline <= CURRENT_TIMESTAMP)))",
                params().addValue("id", id), Integer.class);
    }

    public Integer countPendingGroups(UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group WHERE round_id = :id AND approval_status = 'PENDING' AND status <> 'CANCELLED'",
                params().addValue("id", id), Integer.class);
    }

    /** Exclusion list recorded on the publication audit row ("id status" pairs). */
    public List<String> listExcludedIdStatuses(UUID id) {
        return jdbc.queryForList(
                "SELECT CAST(t.id AS VARCHAR) || ' ' || t.result_status FROM thesis.thesis_topic t "
                        + "WHERE t.round_id = :id AND t.result_status IN ('REVIEW_FAILED', 'WITHDRAWN') ORDER BY t.id",
                params().addValue("id", id), String.class);
    }

    public int markResultsPublishedAt(UUID id) {
        return jdbc.update(
                "UPDATE thesis.thesis_registration_round SET results_published_at = CURRENT_TIMESTAMP WHERE id = :id",
                params().addValue("id", id));
    }

    public Map<String, Object> lockRetraction(UUID id) {
        return one(jdbc,
                "SELECT status, results_published_at FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", ROUND_NOT_FOUND_MESSAGE);
    }

    public int retractToRegistrationClosed(UUID id) {
        return jdbc.update(
                "UPDATE thesis.thesis_registration_round SET status = 'REGISTRATION_CLOSED', "
                        + "results_published_at = NULL, updated_at = CURRENT_TIMESTAMP, version = version + 1 "
                        + "WHERE id = :id AND status = 'RESULTS_PUBLISHED'",
                params().addValue("id", id));
    }

    public Map<String, Object> lockClosure(UUID id) {
        return one(jdbc,
                "SELECT status, name, results_published_at FROM thesis.thesis_registration_round "
                        + "WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", ROUND_NOT_FOUND_MESSAGE);
    }

    public int closeToClosed(UUID id) {
        return jdbc.update(
                "UPDATE thesis.thesis_registration_round SET status = 'CLOSED', updated_at = CURRENT_TIMESTAMP, "
                        + "version = version + 1 WHERE id = :id AND status = 'RESULTS_PUBLISHED'",
                params().addValue("id", id));
    }

    public Integer countTopicsInRound(UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic WHERE round_id = :id",
                params().addValue("id", id), Integer.class);
    }

    public Integer countGroupsInRound(UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group WHERE round_id = :id",
                params().addValue("id", id), Integer.class);
    }

    public Map<String, Object> lockReopen(UUID id) {
        return one(jdbc,
                "SELECT status, name FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", ROUND_NOT_FOUND_MESSAGE);
    }

    public int reopenToResultsPublished(UUID id) {
        return jdbc.update(
                "UPDATE thesis.thesis_registration_round SET status = 'RESULTS_PUBLISHED', "
                        + "updated_at = CURRENT_TIMESTAMP, version = version + 1 "
                        + "WHERE id = :id AND status = 'CLOSED'",
                new MapSqlParameterSource().addValue("id", id));
    }
}
