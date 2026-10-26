package io.campuscore.restfulapi.thesis.repository;

import static io.campuscore.restfulapi.thesis.repository.ThesisMutationSql.one;
import static io.campuscore.restfulapi.thesis.repository.ThesisMutationSql.params;

import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Write-side statements for thesis topic governance: the gate reads that
 * authorize before the locks, the FOR UPDATE topic row locks the governance
 * mutations serialize on, supervisor seeding and directory checks, outcome
 * writes, and the usage counts that keep graded history undeletable.
 */
@Repository
@Profile("persistence")
public class ThesisTopicMutationRepository {

    private static final String TOPIC_NOT_FOUND_MESSAGE = "Thesis topic not found";

    private final NamedParameterJdbcTemplate jdbc;

    public ThesisTopicMutationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Early authorization gate for draft edits: round, owner and department. */
    public Map<String, Object> findGovernanceGate(UUID id) {
        return one(jdbc,
                "SELECT round_id, created_by, department_id FROM thesis.thesis_topic WHERE id = :id",
                params().addValue("id", id), "TOPIC_NOT_FOUND", TOPIC_NOT_FOUND_MESSAGE);
    }

    /** Early authorization gate for approval: round and department. */
    public Map<String, Object> findPublishGate(UUID id) {
        return one(jdbc,
                "SELECT round_id, department_id FROM thesis.thesis_topic WHERE id = :id",
                params().addValue("id", id), "TOPIC_NOT_FOUND", TOPIC_NOT_FOUND_MESSAGE);
    }

    /** Early authorization gate for archiving: round, department and owner. */
    public Map<String, Object> findArchiveGate(UUID id) {
        return one(jdbc,
                "SELECT round_id, department_id, created_by FROM thesis.thesis_topic WHERE id = :id",
                params().addValue("id", id), "TOPIC_NOT_FOUND", TOPIC_NOT_FOUND_MESSAGE);
    }

    /** The round a topic belongs to (mutable-round guard lookup). */
    public Map<String, Object> findTopicRoundId(UUID id) {
        return one(jdbc,
                "SELECT round_id FROM thesis.thesis_topic WHERE id = :id",
                params().addValue("id", id), "TOPIC_NOT_FOUND", TOPIC_NOT_FOUND_MESSAGE);
    }

    /** FOR UPDATE fence every topic governance mutation takes before its entity load. */
    public void lockTopicForUpdate(UUID id) {
        one(jdbc, "SELECT id FROM thesis.thesis_topic WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "TOPIC_NOT_FOUND", TOPIC_NOT_FOUND_MESSAGE);
    }

    /** Locked topic detail the group topic-assignment flow decides on. */
    public Map<String, Object> lockTopicDetail(UUID id) {
        return one(jdbc,
                "SELECT id, round_id, status, max_groups, department_id, final_score, result_status FROM thesis.thesis_topic WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "TOPIC_NOT_FOUND", TOPIC_NOT_FOUND_MESSAGE);
    }

    /** Locked outcome columns of the topic an orphaned group is re-pointed away from. */
    public Map<String, Object> lockTopicOutcome(UUID id) {
        return one(jdbc,
                "SELECT result_status, final_score FROM thesis.thesis_topic WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "TOPIC_NOT_FOUND", TOPIC_NOT_FOUND_MESSAGE);
    }

    /** Locked full binding state of the topic an orphaned group is re-pointed onto. */
    public Map<String, Object> lockTopicForReassign(UUID id) {
        return one(jdbc,
                "SELECT round_id, status, max_groups, result_status, final_score FROM thesis.thesis_topic "
                        + "WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "TOPIC_NOT_FOUND", TOPIC_NOT_FOUND_MESSAGE);
    }

    /** Score/outcome state of the topic a group approval is decided under. */
    public Map<String, Object> findTopicScoreState(UUID id) {
        return one(jdbc,
                "SELECT final_score, result_status FROM thesis.thesis_topic WHERE id = :id",
                params().addValue("id", id), "TOPIC_NOT_FOUND", TOPIC_NOT_FOUND_MESSAGE);
    }

    /** Downstream-work state of the topic an approval withdrawal is decided on. */
    public Map<String, Object> findTopicWorkState(UUID id) {
        return one(jdbc,
                "SELECT gvpb_lecturer_id, result_status, final_score FROM thesis.thesis_topic WHERE id = :id",
                params().addValue("id", id), "TOPIC_NOT_FOUND", TOPIC_NOT_FOUND_MESSAGE);
    }

    /** Outcome columns the withdraw flow re-checks under the topic lock. */
    public Map<String, Object> findOutcomeRow(UUID id) {
        return one(jdbc,
                "SELECT status, final_score, result_status, title FROM thesis.thesis_topic WHERE id = :id",
                params().addValue("id", id), "TOPIC_NOT_FOUND", TOPIC_NOT_FOUND_MESSAGE);
    }

    /** Outcome plus waiver columns the reinstate flow derives the restored state from. */
    public Map<String, Object> findReinstateRow(UUID id) {
        return one(jdbc,
                "SELECT status, final_score, result_status, gvpb_waived_reason, title "
                        + "FROM thesis.thesis_topic WHERE id = :id",
                params().addValue("id", id), "TOPIC_NOT_FOUND", TOPIC_NOT_FOUND_MESSAGE);
    }

    public int markWithdrawn(UUID id, String reason, String by) {
        return jdbc.update(
                "UPDATE thesis.thesis_topic SET result_status = 'WITHDRAWN', result_reason = :reason, "
                        + "result_by = :by, result_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, "
                        + "version = version + 1 WHERE id = :id AND final_score IS NULL",
                params().addValue("id", id).addValue("reason", reason).addValue("by", by));
    }

    public int reinstateOutcome(UUID id, String outcome, String by) {
        return jdbc.update(
                "UPDATE thesis.thesis_topic SET result_status = :outcome, "
                        + "result_reason = CASE WHEN :outcome = 'REVIEW_FAILED' THEN 'Counter-review score below the 5.0 pass threshold' ELSE NULL END, "
                        + "result_by = CASE WHEN :outcome = 'REVIEW_FAILED' THEN :by ELSE NULL END, "
                        + "result_at = CASE WHEN :outcome = 'REVIEW_FAILED' THEN CURRENT_TIMESTAMP ELSE NULL END, "
                        + "updated_at = CURRENT_TIMESTAMP, version = version + 1 "
                        + "WHERE id = :id AND final_score IS NULL AND result_status = 'WITHDRAWN'",
                params().addValue("id", id).addValue("outcome", outcome).addValue("by", by));
    }

    /** Publish gate: a topic needs exactly one or two supervisors. */
    public Integer countSupervisors(UUID topicId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = :topicId",
                params().addValue("topicId", topicId), Integer.class);
    }

    /** Active supervisor assignments whose lecturer profiles can authenticate. */
    public Integer countActiveSupervisors(UUID topicId) {
        return jdbc.queryForObject(
                "SELECT COUNT(DISTINCT s.lecturer_id) FROM thesis.thesis_topic_supervisor s "
                        + "JOIN campuscore_auth.\"Lecturer\" l ON (l.\"id\" = s.lecturer_id "
                        + "OR l.\"userId\" = s.lecturer_id) AND l.\"isActive\" = TRUE "
                        + "JOIN campuscore_auth.\"User\" u ON u.\"id\" = l.\"userId\" "
                        + "AND u.\"status\" = 'ACTIVE' WHERE s.topic_id = :topicId",
                params().addValue("topicId", topicId), Integer.class);
    }

    /** Whether the creator already occupies the first supervisor seat. */
    public Integer countSupervisorOrFirstSeat(UUID topicId, String supervisorId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = :topicId AND (lecturer_id = :supervisorId OR supervisor_order = 1)",
                params().addValue("topicId", topicId).addValue("supervisorId", supervisorId),
                Integer.class);
    }

    public void insertFirstSupervisor(UUID topicId, String lecturerId) {
        jdbc.update(
                "INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) VALUES (:id, :topicId, :lecturerId, 1)",
                params().addValue("id", UUID.randomUUID()).addValue("topicId", topicId)
                        .addValue("lecturerId", lecturerId));
    }

    /** Late topics may only name a real, active lecturer as supervisor. */
    public Integer countActiveLecturers(String supervisorId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"Lecturer\" l "
                        + "JOIN campuscore_auth.\"User\" u ON u.\"id\" = l.\"userId\" "
                        + "AND u.\"status\" = 'ACTIVE' "
                        + "WHERE l.\"id\" = :key AND l.\"isActive\" = TRUE",
                params().addValue("key", supervisorId), Integer.class);
    }

    /** Active-lecturer supervision check; the legacy User.id fallback still resolves. */
    public Integer countActiveSupervision(UUID topicId, String lecturerId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor s "
                        + "JOIN campuscore_auth.\"Lecturer\" l ON (l.\"id\" = s.lecturer_id OR l.\"userId\" = s.lecturer_id) "
                        + "AND l.\"isActive\" = TRUE "
                        + "WHERE s.topic_id = :topicId AND s.lecturer_id = :lecturerId",
                params().addValue("topicId", topicId).addValue("lecturerId", lecturerId),
                Integer.class);
    }

    /** Current-supervision check resolving either the Lecturer id or the actor's User id. */
    public Integer countCurrentSupervision(UUID topicId, String actorId, String lecturerId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor s "
                        + "JOIN campuscore_auth.\"Lecturer\" l ON (l.\"id\" = s.lecturer_id OR l.\"userId\" = s.lecturer_id) "
                        + "AND l.\"isActive\" = TRUE "
                        + "WHERE s.topic_id = :topicId AND (s.lecturer_id = :lecturerId OR s.lecturer_id = :actorId)",
                params().addValue("topicId", topicId)
                        .addValue("actorId", actorId)
                        .addValue("lecturerId", lecturerId),
                Integer.class);
    }

    /** Department of the actor's active lecturer profile, or null without one. */
    public String findActiveLecturerDepartment(String lecturerId) {
        try {
            return jdbc.queryForObject(
                    "SELECT \"departmentId\" FROM campuscore_auth.\"Lecturer\" "
                            + "WHERE \"id\" = :lecturerId AND \"isActive\" = TRUE",
                    params().addValue("lecturerId", lecturerId), String.class);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    public Integer countActiveDepartment(String departmentId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic.\"Department\" WHERE \"id\" = :departmentId AND \"isActive\" = TRUE",
                params().addValue("departmentId", departmentId), Integer.class);
    }

    public Integer countDepartmentInFaculty(String departmentId, String facultyId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic.\"Department\" "
                        + "WHERE \"id\" = :departmentId AND \"facultyId\" = :facultyId AND \"isActive\" = TRUE",
                params().addValue("departmentId", departmentId).addValue("facultyId", facultyId),
                Integer.class);
    }

    /** Any group bound to the topic (archive guard — no predicate). */
    public int countGroupsByTopic(UUID topicId) {
        return count("SELECT COUNT(*) FROM thesis.thesis_group WHERE topic_id = :topicId", "topicId", topicId);
    }

    /** Groups that still occupy one of the topic's limited seats. */
    public int countActiveTopicGroups(UUID topicId) {
        return count("SELECT COUNT(*) FROM thesis.thesis_group WHERE topic_id = :topicId "
                + "AND status <> 'CANCELLED' AND approval_status <> 'REJECTED'", "topicId", topicId);
    }

    public int countCouncilTopics(UUID topicId) {
        return count("SELECT COUNT(*) FROM thesis.thesis_council_topic WHERE topic_id = :topicId", "topicId", topicId);
    }

    public int countTopicScores(UUID topicId) {
        return count("SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = :topicId", "topicId", topicId);
    }

    /** Scores across BOTH sides of a re-point binding freeze it. */
    public Integer countScoresOnTopics(UUID oldTopicId, UUID newTopicId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id IN (:oldId, :newId)",
                params().addValue("oldId", oldTopicId).addValue("newId", newTopicId), Integer.class);
    }

    private int count(String sql, String paramName, UUID value) {
        Integer result = jdbc.queryForObject(sql, params().addValue(paramName, value), Integer.class);
        return result == null ? 0 : result;
    }
}
