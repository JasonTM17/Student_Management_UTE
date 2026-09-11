package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.web.DomainException;
import java.time.Instant;
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
import org.springframework.util.StringUtils;

/**
 * Brief R5: only the group leader submits the topic report. The report is an
 * external-artifact reference (link + metadata), one current version per
 * group, and it is frozen once the round's GVPB grading deadline passes.
 */
@Service
@Profile("persistence")
public class ThesisReportService {

    private final NamedParameterJdbcTemplate jdbc;

    public ThesisReportService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ReportResponse submit(UUID groupId, String title, String url, String note, Jwt actor) {
        GroupContext group = loadGroup(groupId);
        if (!group.leaderStudentId().equals(studentId(actor))) {
            throw new DomainException(HttpStatus.FORBIDDEN, "GROUP_OWNER_REQUIRED",
                    "Only the group leader can submit the topic report");
        }
        requireText(url, "url");
        String trimmedUrl = url.trim();
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            throw invalid("url must be an http(s) link to the report document");
        }
        Instant gvpbDeadline = group.gvpbDeadline();
        if (gvpbDeadline != null && !Instant.now().isBefore(gvpbDeadline)) {
            throw conflict("REPORT_DEADLINE_PASSED",
                    "The GVPB grading deadline for this round has passed; reports are frozen");
        }
        Integer approved = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group WHERE id = :groupId AND approval_status = 'APPROVED'",
                params().addValue("groupId", groupId), Integer.class);
        if (approved == null || approved == 0) {
            throw conflict("GROUP_STATE_CONFLICT", "Reports can be submitted only after the group is approved");
        }
        jdbc.update("DELETE FROM thesis.thesis_group_report WHERE group_id = :groupId",
                params().addValue("groupId", groupId));
        jdbc.update(
                "INSERT INTO thesis.thesis_group_report (id, group_id, round_id, submitted_by, title, url, note) "
                        + "VALUES (:id, :groupId, :roundId, :submittedBy, :title, :url, :note)",
                params().addValue("id", UUID.randomUUID())
                        .addValue("groupId", groupId)
                        .addValue("roundId", group.roundId())
                        .addValue("submittedBy", studentId(actor))
                        .addValue("title", blankToNull(title, 240))
                        .addValue("url", trimmedUrl)
                        .addValue("note", blankToNull(note, 500)));
        return get(groupId, actor);
    }

    @Transactional(readOnly = true)
    public ReportResponse get(UUID groupId, Jwt actor) {
        GroupContext group = loadGroup(groupId);
        boolean member = count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = :groupId AND student_id = :studentId",
                params().addValue("groupId", groupId).addValue("studentId", studentId(actor))) > 0;
        boolean supervisor = StringUtils.hasText(lecturerId(actor))
                && count("SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = :topicId AND lecturer_id = :lecturerId",
                        params().addValue("topicId", group.topicId()).addValue("lecturerId", lecturerId(actor))) > 0;
        boolean councilMember = StringUtils.hasText(lecturerId(actor))
                && group.topicId() != null
                && count("SELECT COUNT(*) FROM thesis.thesis_council_member cm "
                        + "JOIN thesis.thesis_council_topic ct ON ct.council_id = cm.council_id "
                        + "WHERE ct.topic_id = :topicId AND cm.lecturer_id = :lecturerId",
                        params().addValue("topicId", group.topicId()).addValue("lecturerId", lecturerId(actor))) > 0;
        boolean admin = hasRole(actor, "ADMIN") || hasRole(actor, "TRUONG_KHOA");
        if (!member && !supervisor && !councilMember && !admin) {
            throw new DomainException(HttpStatus.FORBIDDEN, "GROUP_MEMBER_REQUIRED",
                    "Only group members, supervisors, council reviewers, or staff can read the report");
        }
        try {
            return jdbc.queryForObject(
                    "SELECT group_id, title, url, note, submitted_by, submitted_at, updated_at "
                            + "FROM thesis.thesis_group_report WHERE group_id = :groupId",
                    params().addValue("groupId", groupId),
                    (rs, ignored) -> new ReportResponse(
                            UUID.fromString(rs.getString("group_id")),
                            rs.getString("title"),
                            rs.getString("url"),
                            rs.getString("note"),
                            rs.getString("submitted_by"),
                            rs.getObject("submitted_at", java.time.OffsetDateTime.class).toInstant(),
                            rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant()));
        } catch (EmptyResultDataAccessException exception) {
            throw notFound("REPORT_NOT_FOUND", "The group has not submitted a report yet");
        }
    }

    @Transactional(readOnly = true)
    public ReportResponse getByTopic(UUID topicId, Jwt actor) {
        java.util.List<UUID> groupIds = jdbc.query(
                "SELECT id FROM thesis.thesis_group WHERE topic_id = :topicId AND approval_status = 'APPROVED'",
                params().addValue("topicId", topicId),
                (rs, ignored) -> UUID.fromString(rs.getString("id")));
        if (groupIds.isEmpty()) {
            return null;
        }
        try {
            return get(groupIds.get(0), actor);
        } catch (DomainException exception) {
            if (exception.getStatus() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw exception;
        }
    }

    private GroupContext loadGroup(UUID groupId) {
        Map<String, Object> group = one(
                "SELECT g.id, g.round_id, g.leader_student_id, g.topic_id, r.gvpb_deadline "
                        + "FROM thesis.thesis_group g JOIN thesis.thesis_registration_round r ON r.id = g.round_id "
                        + "WHERE g.id = :groupId",
                params().addValue("groupId", groupId), "GROUP_NOT_FOUND", "Thesis group not found");
        return new GroupContext(
                (UUID) group.get("id"),
                (UUID) group.get("round_id"),
                (String) group.get("leader_student_id"),
                (UUID) group.get("topic_id"),
                group.get("gvpb_deadline") == null ? null : instantOf(group.get("gvpb_deadline")));
    }

    private record GroupContext(UUID id, UUID roundId, String leaderStudentId, UUID topicId, Instant gvpbDeadline) { }

    public record ReportResponse(
            UUID groupId,
            String title,
            String url,
            String note,
            String submittedBy,
            Instant submittedAt,
            Instant updatedAt) { }

    private static Instant instantOf(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        return null;
    }

    private static String blankToNull(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw invalid("Text must contain at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private int count(String sql, MapSqlParameterSource parameters) {
        Integer result = jdbc.queryForObject(sql, parameters, Integer.class);
        return result == null ? 0 : result;
    }

    private Map<String, Object> one(String sql, MapSqlParameterSource parameters, String code, String message) {
        try {
            return jdbc.queryForMap(sql, parameters);
        } catch (EmptyResultDataAccessException exception) {
            throw notFound(code, message);
        }
    }

    private static String subject(Jwt actor) {
        return actor == null || actor.getSubject() == null ? "" : actor.getSubject();
    }

    private static String studentId(Jwt actor) {
        return normalize(actor == null ? null : actor.getClaimAsString("studentId"));
    }

    private static String lecturerId(Jwt actor) {
        return normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static MapSqlParameterSource params() {
        return new MapSqlParameterSource();
    }

    private static boolean hasRole(Jwt actor, String role) {
        var roles = actor == null ? null : actor.getClaimAsStringList("roles");
        return roles != null && roles.contains(role);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw invalid(name + " is required");
    }

    private static DomainException invalid(String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static DomainException conflict(String code, String message) {
        return new DomainException(HttpStatus.CONFLICT, code, message);
    }

    private static DomainException notFound(String code, String message) {
        return new DomainException(HttpStatus.NOT_FOUND, code, message);
    }
}
