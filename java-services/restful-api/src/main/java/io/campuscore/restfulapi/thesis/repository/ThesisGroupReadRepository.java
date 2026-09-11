package io.campuscore.restfulapi.thesis.repository;

import io.campuscore.restfulapi.thesis.domain.ApprovalStatus;
import io.campuscore.restfulapi.thesis.domain.GroupStatus;
import io.campuscore.restfulapi.thesis.web.ThesisGroupReadDtos.GroupMemberResponse;
import io.campuscore.restfulapi.thesis.web.ThesisGroupReadDtos.GroupResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

/** JDBC read adapter for thesis groups in the course database. */
@Repository
@Profile("persistence")
public class ThesisGroupReadRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ThesisGroupReadRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<GroupResponse> findByRoundId(UUID roundId) {
        List<GroupRow> rows = jdbc.query("SELECT id, round_id, leader_student_id, topic_id, status, approval_status, rejection_reason FROM thesis.thesis_group WHERE round_id = :roundId ORDER BY created_at DESC", new MapSqlParameterSource("roundId", roundId), ThesisGroupReadRepository::row);
        return hydrate(rows);
    }

    public List<GroupResponse> findByRoundIdAndStudentId(UUID roundId, String studentId) {
        List<GroupRow> rows = jdbc.query(
                "SELECT g.id, g.round_id, g.leader_student_id, g.topic_id, g.status, "
                        + "g.approval_status, g.rejection_reason FROM thesis.thesis_group g "
                        + "JOIN thesis.thesis_group_member m ON m.group_id = g.id "
                        + "WHERE g.round_id = :roundId AND m.student_id = :studentId "
                        + "ORDER BY g.created_at DESC",
                new MapSqlParameterSource("roundId", roundId).addValue("studentId", studentId),
                ThesisGroupReadRepository::row);
        return hydrate(rows);
    }

    public GroupResponse findById(UUID id) {
        List<GroupRow> rows = jdbc.query("SELECT id, round_id, leader_student_id, topic_id, status, approval_status, rejection_reason FROM thesis.thesis_group WHERE id = :id", new MapSqlParameterSource("id", id), ThesisGroupReadRepository::row);
        return hydrate(rows).stream().findFirst().orElse(null);
    }

    public GroupResponse findByIdAndStudentId(UUID id, String studentId) {
        List<GroupRow> rows = jdbc.query(
                "SELECT g.id, g.round_id, g.leader_student_id, g.topic_id, g.status, "
                        + "g.approval_status, g.rejection_reason FROM thesis.thesis_group g "
                        + "JOIN thesis.thesis_group_member m ON m.group_id = g.id "
                        + "WHERE g.id = :id AND m.student_id = :studentId",
                new MapSqlParameterSource("id", id).addValue("studentId", studentId),
                ThesisGroupReadRepository::row);
        return hydrate(rows).stream().findFirst().orElse(null);
    }

    private List<GroupResponse> hydrate(List<GroupRow> rows) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, List<String>> members = new LinkedHashMap<>();
        Map<UUID, List<GroupMemberResponse>> memberDetails = new LinkedHashMap<>();
        for (GroupRow row : rows) {
            members.put(row.id(), new ArrayList<>());
            memberDetails.put(row.id(), new ArrayList<>());
        }
        jdbc.query("SELECT m.group_id, m.student_id, m.display_name, m.contact, m.is_external, m.is_leader, m.member_order, "
                + "s.\"studentId\" AS student_number, u.\"firstName\" AS first_name, u.\"lastName\" AS last_name, u.\"email\" AS email "
                + "FROM thesis.thesis_group_member m "
                + "LEFT JOIN campuscore_auth.\"Student\" s ON s.\"id\" = m.student_id "
                + "LEFT JOIN campuscore_auth.\"User\" u ON u.\"id\" = s.\"userId\" "
                + "WHERE m.group_id IN (:ids) "
                + "ORDER BY m.group_id, m.member_order", new MapSqlParameterSource("ids", members.keySet()), (RowCallbackHandler) rs -> {
            UUID groupId = rs.getObject("group_id", UUID.class);
            String studentId = rs.getString("student_id");
            boolean external = rs.getBoolean("is_external");
            String displayName = rs.getString("display_name");
            String contact = rs.getString("contact");
            String profileName = blankToNull(joinName(rs.getString("first_name"), rs.getString("last_name")));
            members.get(groupId).add(studentId);
            memberDetails.get(groupId).add(new GroupMemberResponse(
                    studentId,
                    external ? null : rs.getString("student_number"),
                    external ? displayName : profileName,
                    external ? contact : rs.getString("email"),
                    external,
                    rs.getBoolean("is_leader"),
                    rs.getInt("member_order")));
        });
        return rows.stream().map(row -> new GroupResponse(row.id(), row.roundId(), row.leaderStudentId(), row.topicId(), row.status(), row.approvalStatus(), row.rejectionReason(), List.copyOf(members.get(row.id())), List.copyOf(memberDetails.get(row.id())))).toList();
    }

    private static String joinName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        return (first + " " + last).trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static GroupRow row(ResultSet rs, int ignored) throws SQLException {
        return new GroupRow(rs.getObject("id", UUID.class), rs.getObject("round_id", UUID.class), rs.getString("leader_student_id"), rs.getObject("topic_id", UUID.class), GroupStatus.valueOf(rs.getString("status")), ApprovalStatus.valueOf(rs.getString("approval_status")), rs.getString("rejection_reason"));
    }
    private record GroupRow(UUID id, UUID roundId, String leaderStudentId, UUID topicId, GroupStatus status, ApprovalStatus approvalStatus, String rejectionReason) { }
}
