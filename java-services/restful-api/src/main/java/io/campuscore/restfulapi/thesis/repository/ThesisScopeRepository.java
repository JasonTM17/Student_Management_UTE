package io.campuscore.restfulapi.thesis.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-side scope and visibility statements for thesis governance: dean and
 * department-head profile resolution, topic-in-faculty and supervision
 * checks, council-grader lookups, publication state, and counter-review
 * scores. The static scope helpers delegate here so every governance service
 * shares one statement set.
 */
@Repository
@Profile("persistence")
public class ThesisScopeRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ThesisScopeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Faculty of the dean's active, subject-bound lecturer profile. */
    public List<String> findDeanFacultyIds(String lecturerId, String subject) {
        return jdbc.queryForList(
                "SELECT f.\"id\" FROM campuscore_auth.\"Lecturer\" l "
                        + "JOIN campuscore_auth.\"User\" u ON u.\"id\" = l.\"userId\" "
                        + "JOIN academic.\"Department\" d ON d.\"id\" = l.\"departmentId\" "
                        + "JOIN academic.\"Faculty\" f ON f.\"id\" = d.\"facultyId\" "
                        + "WHERE l.\"id\" = :lecturerId AND l.\"userId\" = :subject "
                        + "AND l.\"isActive\" = TRUE AND u.\"status\" = 'ACTIVE' "
                        + "AND d.\"isActive\" = TRUE AND f.\"isActive\" = TRUE",
                new MapSqlParameterSource("lecturerId", lecturerId)
                        .addValue("subject", subject), String.class);
    }

    /** Department of the head's active, subject-bound lecturer profile. */
    public List<String> findHeadDepartmentIds(String lecturerId, String subject) {
        return jdbc.queryForList(
                "SELECT l.\"departmentId\" FROM campuscore_auth.\"Lecturer\" l "
                        + "JOIN campuscore_auth.\"User\" u ON u.\"id\" = l.\"userId\" "
                        + "JOIN academic.\"Department\" d ON d.\"id\" = l.\"departmentId\" "
                        + "WHERE l.\"id\" = :lecturerId AND l.\"userId\" = :subject "
                        + "AND l.\"isActive\" = TRUE AND u.\"status\" = 'ACTIVE' AND d.\"isActive\" = TRUE",
                new MapSqlParameterSource("lecturerId", lecturerId)
                        .addValue("subject", subject), String.class);
    }

    public Integer countTopicInFaculty(UUID topicId, String facultyId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic t "
                        + "JOIN academic.\"Department\" d ON d.\"id\" = t.department_id "
                        + "WHERE t.id = :topicId AND d.\"facultyId\" = :facultyId",
                new MapSqlParameterSource("topicId", topicId).addValue("facultyId", facultyId),
                Integer.class);
    }

    public Integer countTopicSupervisor(UUID topicId, String lecturerId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor "
                        + "WHERE topic_id = :topicId AND lecturer_id = :lecturerId",
                new MapSqlParameterSource("topicId", topicId).addValue("lecturerId", lecturerId),
                Integer.class);
    }

    /** Faculty a topic's department resolves to (one row, possibly null). */
    public List<String> findTopicFacultyIds(UUID topicId) {
        return jdbc.queryForList(
                "SELECT f.\"id\" FROM thesis.thesis_topic t "
                        + "LEFT JOIN academic.\"Department\" d ON d.\"id\" = t.department_id "
                        + "LEFT JOIN academic.\"Faculty\" f ON f.\"id\" = d.\"facultyId\" "
                        + "WHERE t.id = :topicId",
                new MapSqlParameterSource("topicId", topicId), String.class);
    }

    public Integer countDepartmentInFaculty(String departmentId, String facultyId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic.\"Department\" WHERE \"id\" = :departmentId AND \"facultyId\" = :facultyId",
                new MapSqlParameterSource("departmentId", departmentId).addValue("facultyId", facultyId),
                Integer.class);
    }

    /** Counter-review scores for a topic, newest first. */
    public List<BigDecimal> findGvpbScoresNewestFirst(UUID topicId) {
        return jdbc.queryForList(
                "SELECT score FROM thesis.thesis_topic_score "
                        + "WHERE topic_id = :topicId AND component = 'GVPB' "
                        + "ORDER BY graded_at DESC, created_at DESC",
                new MapSqlParameterSource("topicId", topicId), BigDecimal.class);
    }

    public List<String> findDepartmentIdsInFaculty(String facultyId) {
        return jdbc.queryForList(
                "SELECT \"id\" FROM academic.\"Department\" WHERE \"facultyId\" = :facultyId",
                new MapSqlParameterSource("facultyId", facultyId),
                String.class);
    }

    public List<UUID> findSupervisedTopicIds(String lecturerId, List<UUID> topicIds) {
        return jdbc.queryForList(
                "SELECT topic_id FROM thesis.thesis_topic_supervisor WHERE lecturer_id = :lecturerId AND topic_id IN (:topicIds)",
                new MapSqlParameterSource()
                        .addValue("lecturerId", lecturerId)
                        .addValue("topicIds", topicIds),
                UUID.class);
    }

    public List<UUID> findGradingCouncilTopicIds(String lecturerId, List<UUID> topicIds) {
        return jdbc.queryForList(
                "SELECT ct.topic_id FROM thesis.thesis_council_topic ct "
                        + "JOIN thesis.thesis_council_member m ON m.council_id = ct.council_id "
                        + "WHERE m.lecturer_id = :lecturerId AND ct.topic_id IN (:topicIds)",
                new MapSqlParameterSource()
                        .addValue("lecturerId", lecturerId)
                        .addValue("topicIds", topicIds),
                UUID.class);
    }

    /**
     * Topics a student took part in through an approved group — after results
     * publication these are the only topic outcomes a student may read, the
     * same participation scope {@code /me/results} applies.
     */
    public List<UUID> findParticipatedTopicIds(String studentId, List<UUID> topicIds) {
        if (topicIds.isEmpty()) {
            return List.of();
        }
        return jdbc.queryForList(
                "SELECT DISTINCT g.topic_id FROM thesis.thesis_group g "
                        + "JOIN thesis.thesis_group_member m ON m.group_id = g.id "
                        + "WHERE m.student_id = :studentId AND g.approval_status = 'APPROVED' "
                        + "AND g.topic_id IN (:topicIds)",
                new MapSqlParameterSource()
                        .addValue("studentId", studentId)
                        .addValue("topicIds", topicIds),
                UUID.class);
    }

    public Integer countCouncilGrader(UUID topicId, String lecturerId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic ct "
                        + "JOIN thesis.thesis_council_member m ON m.council_id = ct.council_id "
                        + "WHERE ct.topic_id = :topicId AND m.lecturer_id = :lecturerId",
                new MapSqlParameterSource()
                        .addValue("topicId", topicId)
                        .addValue("lecturerId", lecturerId),
                Integer.class);
    }

    public Integer countPublishedResultsRounds(UUID roundId) {
        // CLOSED counts as published: the archive state still serves scores.
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_registration_round WHERE id = :roundId "
                        + "AND status IN ('RESULTS_PUBLISHED', 'CLOSED')",
                new MapSqlParameterSource().addValue("roundId", roundId),
                Integer.class);
    }
}
