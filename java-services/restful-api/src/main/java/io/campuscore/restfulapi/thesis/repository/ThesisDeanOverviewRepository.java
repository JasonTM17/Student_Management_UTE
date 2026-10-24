package io.campuscore.restfulapi.thesis.repository;

import io.campuscore.restfulapi.thesis.model.ApprovalStatus;
import io.campuscore.restfulapi.thesis.model.GroupStatus;
import io.campuscore.restfulapi.thesis.dto.ThesisDeanOverviewDtos.DeanGroup;
import io.campuscore.restfulapi.thesis.dto.ThesisDeanOverviewDtos.DeanMember;
import io.campuscore.restfulapi.thesis.dto.ThesisDeanOverviewDtos.DeanSupervisor;
import io.campuscore.restfulapi.thesis.dto.ThesisDeanOverviewDtos.DepartmentCount;
import io.campuscore.restfulapi.thesis.dto.ThesisDeanOverviewDtos.FacultyScope;
import io.campuscore.restfulapi.thesis.dto.ThesisDeanOverviewDtos.RoundInfo;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC read adapter for the faculty-head observation dashboard. Every query is
 * scoped to the dean's own faculty, resolved live from the bound Lecturer
 * profile — a stale JWT claim can never widen the scope because the lecturer
 * row must still belong to the token subject and stay active.
 */
@Repository
@Profile("persistence")
public class ThesisDeanOverviewRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ThesisDeanOverviewRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The dean's live department + faculty; empty when the profile is gone or inactive. */
    public Optional<FacultyScope> findDeanFaculty(String subject, String lecturerId) {
        List<FacultyScope> rows = jdbc.query(
                "SELECT f.\"id\" AS faculty_id, f.\"name\" AS faculty_name, "
                        + "d.\"id\" AS department_id, d.\"name\" AS department_name "
                        + "FROM campuscore_auth.\"Lecturer\" l "
                        + "JOIN campuscore_auth.\"User\" u ON u.\"id\" = l.\"userId\" "
                        + "JOIN academic.\"Department\" d ON d.\"id\" = l.\"departmentId\" "
                        + "JOIN academic.\"Faculty\" f ON f.\"id\" = d.\"facultyId\" "
                        + "WHERE l.\"id\" = :lecturerId AND l.\"userId\" = :subject "
                        + "AND l.\"isActive\" = TRUE AND u.\"status\" = 'ACTIVE' AND d.\"isActive\" = TRUE "
                        + "AND f.\"isActive\" = TRUE",
                new MapSqlParameterSource("lecturerId", lecturerId).addValue("subject", subject),
                (rs, ignored) -> new FacultyScope(
                        rs.getString("faculty_id"),
                        rs.getString("faculty_name"),
                        rs.getString("department_id"),
                        rs.getString("department_name")));
        return rows.stream().findFirst();
    }

    public Optional<RoundInfo> findRound(UUID roundId) {
        List<RoundInfo> rows = jdbc.query(
                "SELECT id, name, thesis_type, status FROM thesis.thesis_registration_round WHERE id = :roundId",
                new MapSqlParameterSource("roundId", roundId),
                (rs, ignored) -> new RoundInfo(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("thesis_type"),
                        rs.getString("status")));
        return rows.stream().findFirst();
    }

    /**
     * Groups of the round that belong to the faculty: either through their
     * assigned topic's department, or — while a group still has no topic —
     * through at least one internal member's curriculum department.
     */
    public List<DeanGroup> findFacultyGroups(UUID roundId, String facultyId) {
        List<DeanGroup> groups = jdbc.query(
                "SELECT g.id, g.status, g.approval_status, g.rejection_reason, g.created_at, "
                        + "t.id AS topic_id, t.title AS topic_title, t.department_id, dept.\"name\" AS department_name "
                        + "FROM thesis.thesis_group g "
                        + "LEFT JOIN thesis.thesis_topic t ON t.id = g.topic_id "
                        + "LEFT JOIN academic.\"Department\" dept ON dept.\"id\" = t.department_id "
                        + "WHERE g.round_id = :roundId AND ("
                        + "  dept.\"facultyId\" = :facultyId "
                        + "  OR (g.topic_id IS NULL AND EXISTS ("
                        + "    SELECT 1 FROM thesis.thesis_group_member m "
                        + "    JOIN campuscore_auth.\"Student\" s ON s.\"id\" = m.student_id "
                        + "    JOIN academic.\"Curriculum\" c ON c.\"id\" = s.\"curriculumId\" "
                        + "    JOIN academic.\"Department\" md ON md.\"id\" = c.\"departmentId\" "
                        + "    WHERE m.group_id = g.id AND md.\"facultyId\" = :facultyId)) "
                        + ") ORDER BY g.created_at DESC, g.id",
                new MapSqlParameterSource("roundId", roundId).addValue("facultyId", facultyId),
                (rs, ignored) -> new DeanGroup(
                        rs.getObject("id", UUID.class),
                        GroupStatus.valueOf(rs.getString("status")),
                        ApprovalStatus.valueOf(rs.getString("approval_status")),
                        rs.getString("rejection_reason"),
                        rs.getObject("topic_id", UUID.class),
                        rs.getString("topic_title"),
                        rs.getString("department_id"),
                        rs.getString("department_name"),
                        List.of(),
                        List.of(),
                        timestampToInstant(rs.getTimestamp("created_at"))));
        if (groups.isEmpty()) {
            return groups;
        }
        Map<UUID, List<DeanSupervisor>> supervisors = findSupervisors(
                groups.stream().map(DeanGroup::topicId).filter(id -> id != null).toList());
        Map<UUID, List<DeanMember>> members = findMembers(
                groups.stream().map(DeanGroup::id).toList());
        return groups.stream()
                .map(g -> new DeanGroup(g.id(), g.status(), g.approvalStatus(), g.rejectionReason(),
                        g.topicId(), g.topicTitle(), g.departmentId(), g.departmentName(),
                        supervisors.getOrDefault(g.topicId(), List.of()),
                        members.getOrDefault(g.id(), List.of()),
                        g.createdAt()))
                .toList();
    }

    /** Topic rows of the faculty inside the round, for per-department stats. */
    public List<DepartmentCount> findDepartmentCounts(UUID roundId, String facultyId) {
        return jdbc.query(
                "SELECT d.\"id\" AS department_id, d.\"name\" AS department_name, "
                        + "COUNT(DISTINCT t.id) AS topic_count "
                        + "FROM academic.\"Department\" d "
                        + "LEFT JOIN thesis.thesis_topic t ON t.department_id = d.\"id\" AND t.round_id = :roundId "
                        + "WHERE d.\"facultyId\" = :facultyId AND d.\"isActive\" = TRUE "
                        + "GROUP BY d.\"id\", d.\"name\" ORDER BY d.\"name\"",
                new MapSqlParameterSource("roundId", roundId).addValue("facultyId", facultyId),
                (rs, ignored) -> new DepartmentCount(
                        rs.getString("department_id"),
                        rs.getString("department_name"),
                        0,
                        rs.getInt("topic_count")));
    }

    private Map<UUID, List<DeanSupervisor>> findSupervisors(Collection<UUID> topicIds) {
        Map<UUID, List<DeanSupervisor>> byTopic = new LinkedHashMap<>();
        if (topicIds.isEmpty()) {
            return byTopic;
        }
        jdbc.query(
                "SELECT ts.topic_id, ts.lecturer_id, ts.supervisor_order, "
                        + "l.\"employeeId\", u.\"firstName\", u.\"lastName\", d.\"name\" AS department_name "
                        + "FROM thesis.thesis_topic_supervisor ts "
                        + "LEFT JOIN campuscore_auth.\"Lecturer\" l ON l.\"id\" = ts.lecturer_id "
                        + "LEFT JOIN campuscore_auth.\"User\" u ON u.\"id\" = l.\"userId\" "
                        + "LEFT JOIN academic.\"Department\" d ON d.\"id\" = l.\"departmentId\" "
                        + "WHERE ts.topic_id IN (:topicIds) "
                        + "ORDER BY ts.topic_id, ts.supervisor_order",
                new MapSqlParameterSource("topicIds", topicIds),
                (RowCallbackHandler) rs -> byTopic
                        .computeIfAbsent(rs.getObject("topic_id", UUID.class), k -> new ArrayList<>())
                        .add(new DeanSupervisor(
                                rs.getString("lecturer_id"),
                                rs.getString("employeeId"),
                                blankToNull(joinName(rs.getString("firstName"), rs.getString("lastName"))),
                                rs.getString("department_name"),
                                rs.getInt("supervisor_order"))));
        return byTopic;
    }

    private Map<UUID, List<DeanMember>> findMembers(Collection<UUID> groupIds) {
        Map<UUID, List<DeanMember>> byGroup = new LinkedHashMap<>();
        jdbc.query(
                "SELECT m.group_id, m.display_name, m.is_external, m.is_leader, m.member_order, "
                        + "s.\"studentId\" AS student_number, u.\"firstName\", u.\"lastName\" "
                        + "FROM thesis.thesis_group_member m "
                        + "LEFT JOIN campuscore_auth.\"Student\" s ON s.\"id\" = m.student_id "
                        + "LEFT JOIN campuscore_auth.\"User\" u ON u.\"id\" = s.\"userId\" "
                        + "WHERE m.group_id IN (:groupIds) "
                        + "ORDER BY m.group_id, m.member_order",
                new MapSqlParameterSource("groupIds", groupIds),
                (RowCallbackHandler) rs -> {
                    boolean external = rs.getBoolean("is_external");
                    byGroup.computeIfAbsent(rs.getObject("group_id", UUID.class), k -> new ArrayList<>())
                            .add(new DeanMember(
                                    external ? null : rs.getString("student_number"),
                                    external ? rs.getString("display_name")
                                            : blankToNull(joinName(rs.getString("firstName"), rs.getString("lastName"))),
                                    null,
                                    external,
                                    rs.getBoolean("is_leader"),
                                    rs.getInt("member_order")));
                });
        return byGroup;
    }

    private static String joinName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        return (last + " " + first).trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Instant timestampToInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
