package io.campuscore.restfulapi.thesis.service;

import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only lecturer workload aggregation used by the campus assistant:
 * every topic the caller supervises (with registered-group counts) and every
 * defense council the caller sits on (with assigned-topic counts), across all
 * rounds in one query pair. The assistant must not scan rounds and fan out
 * per-topic supervisor lookups client-side, so this is the server-side join.
 */
@Service
@Profile("persistence")
public class ThesisLecturerWorkloadService {

    private final NamedParameterJdbcTemplate jdbc;

    public ThesisLecturerWorkloadService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public LecturerWorkload workload(String lecturerId) {
        List<SupervisedTopic> topics = jdbc.query(
                "SELECT ts.topic_id, t.title, t.status, t.round_id, r.name AS round_name, r.status AS round_status, "
                        + "MAX(r.report_date) AS report_date, "
                        + "COUNT(g.id) AS group_count, "
                        + "COUNT(g.id) FILTER (WHERE g.approval_status = 'PENDING') AS pending_count "
                        + "FROM thesis.thesis_topic_supervisor ts "
                        + "JOIN thesis.thesis_topic t ON t.id = ts.topic_id "
                        + "JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "LEFT JOIN thesis.thesis_group g ON g.topic_id = t.id "
                        + "WHERE ts.lecturer_id = :lecturerId "
                        + "GROUP BY ts.topic_id, t.title, t.status, t.round_id, r.name, r.status "
                        + "ORDER BY MAX(r.created_at) DESC, MAX(t.created_at) DESC LIMIT 100",
                new MapSqlParameterSource("lecturerId", lecturerId),
                (rs, ignored) -> new SupervisedTopic(
                        rs.getObject("topic_id", UUID.class),
                        rs.getString("title"),
                        rs.getString("status"),
                        rs.getObject("round_id", UUID.class),
                        rs.getString("round_name"),
                        rs.getString("round_status"),
                        instantOf(rs, "report_date"),
                        rs.getInt("group_count"),
                        rs.getInt("pending_count")));
        List<CouncilAssignment> councils = jdbc.query(
                "SELECT cm.council_id, c.name, cm.member_role, c.round_id, r.name AS round_name, r.status AS round_status, "
                        + "MAX(r.report_date) AS report_date, MAX(r.gvpb_deadline) AS gvpb_deadline, "
                        + "COUNT(ct.topic_id) AS topic_count "
                        + "FROM thesis.thesis_council_member cm "
                        + "JOIN thesis.thesis_council c ON c.id = cm.council_id "
                        + "JOIN thesis.thesis_registration_round r ON r.id = c.round_id "
                        + "LEFT JOIN thesis.thesis_council_topic ct ON ct.council_id = cm.council_id "
                        + "WHERE cm.lecturer_id = :lecturerId "
                        + "GROUP BY cm.council_id, c.name, cm.member_role, c.round_id, r.name, r.status "
                        + "ORDER BY MAX(r.created_at) DESC LIMIT 50",
                new MapSqlParameterSource("lecturerId", lecturerId),
                (rs, ignored) -> new CouncilAssignment(
                        rs.getObject("council_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("member_role"),
                        rs.getObject("round_id", UUID.class),
                        rs.getString("round_name"),
                        rs.getString("round_status"),
                        instantOf(rs, "report_date"),
                        instantOf(rs, "gvpb_deadline"),
                        rs.getInt("topic_count")));
        return new LecturerWorkload(List.copyOf(topics), List.copyOf(councils));
    }

    private static java.time.Instant instantOf(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record SupervisedTopic(
            UUID topicId,
            String title,
            String topicStatus,
            UUID roundId,
            String roundName,
            String roundStatus,
            java.time.Instant reportDate,
            int groupCount,
            int pendingGroupCount) { }

    public record CouncilAssignment(
            UUID councilId,
            String name,
            String memberRole,
            UUID roundId,
            String roundName,
            String roundStatus,
            java.time.Instant reportDate,
            java.time.Instant gvpbDeadline,
            int topicCount) { }

    public record LecturerWorkload(
            List<SupervisedTopic> topics,
            List<CouncilAssignment> councils) { }
}
