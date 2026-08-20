package io.campuscore.restfulapi.thesis.repository;

import io.campuscore.restfulapi.thesis.domain.ApprovalStatus;
import io.campuscore.restfulapi.thesis.domain.GroupStatus;
import io.campuscore.restfulapi.thesis.web.ThesisGroupReadDtos.GroupResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

/** JDBC-only read adapter: this candidate never owns or mutates legacy groups. */
@Repository
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.thesis-read", name = "enabled", havingValue = "true")
public class ThesisGroupReadRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ThesisGroupReadRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<GroupResponse> findByRoundId(UUID roundId) {
        List<GroupRow> rows = jdbc.query("SELECT id, round_id, leader_student_id, topic_id, status, approval_status, rejection_reason FROM thesis.thesis_group WHERE round_id = :roundId ORDER BY created_at DESC", new MapSqlParameterSource("roundId", roundId), ThesisGroupReadRepository::row);
        return hydrate(rows);
    }

    public GroupResponse findById(UUID id) {
        List<GroupRow> rows = jdbc.query("SELECT id, round_id, leader_student_id, topic_id, status, approval_status, rejection_reason FROM thesis.thesis_group WHERE id = :id", new MapSqlParameterSource("id", id), ThesisGroupReadRepository::row);
        return hydrate(rows).stream().findFirst().orElse(null);
    }

    private List<GroupResponse> hydrate(List<GroupRow> rows) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, List<UUID>> members = new LinkedHashMap<>();
        for (GroupRow row : rows) members.put(row.id(), new ArrayList<>());
        jdbc.query("SELECT group_id, student_id FROM thesis.thesis_group_member WHERE group_id IN (:ids) ORDER BY group_id, member_order", new MapSqlParameterSource("ids", members.keySet()), (RowCallbackHandler) rs -> members.get(rs.getObject("group_id", UUID.class)).add(rs.getObject("student_id", UUID.class)));
        return rows.stream().map(row -> new GroupResponse(row.id(), row.roundId(), row.leaderStudentId(), row.topicId(), row.status(), row.approvalStatus(), row.rejectionReason(), List.copyOf(members.get(row.id())))).toList();
    }

    private static GroupRow row(ResultSet rs, int ignored) throws SQLException {
        return new GroupRow(rs.getObject("id", UUID.class), rs.getObject("round_id", UUID.class), rs.getObject("leader_student_id", UUID.class), rs.getObject("topic_id", UUID.class), GroupStatus.valueOf(rs.getString("status")), ApprovalStatus.valueOf(rs.getString("approval_status")), rs.getString("rejection_reason"));
    }
    private record GroupRow(UUID id, UUID roundId, UUID leaderStudentId, UUID topicId, GroupStatus status, ApprovalStatus approvalStatus, String rejectionReason) { }
}
