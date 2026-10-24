package io.campuscore.restfulapi.thesis.scope;

import io.campuscore.restfulapi.exception.DomainException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Faculty scope for a faculty head (TRUONG_KHOA). Rounds and councils are
 * university-wide entities, but every topic-level governance write a dean
 * performs must stay inside their bound, active faculty — global admins
 * (ADMIN/SUPER_ADMIN) and non-dean actors pass through untouched.
 */
public final class ThesisDeanScope {

    private ThesisDeanScope() { }

    public static boolean isFacultyHead(Jwt actor) {
        List<String> roles = actor == null ? null : actor.getClaimAsStringList("roles");
        return roles != null && roles.contains("TRUONG_KHOA");
    }

    public static boolean isGlobalAdmin(Jwt actor) {
        List<String> roles = actor == null ? null : actor.getClaimAsStringList("roles");
        return roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN"));
    }

    /**
     * Resolves the dean's faculty from the live, subject-bound active lecturer
     * profile. Mirrors ThesisGroupReadRepository.findLecturerScope without a
     * repository dependency so every governance service can share it.
     */
    public static String facultyId(NamedParameterJdbcTemplate jdbc, Jwt actor) {
        if (!isFacultyHead(actor) || actor.getSubject() == null) {
            return null;
        }
        String lecturerId = actor.getClaimAsString("lecturerId");
        if (lecturerId == null || lecturerId.isBlank()) {
            return null;
        }
        List<String> rows = jdbc.queryForList(
                "SELECT f.\"id\" FROM campuscore_auth.\"Lecturer\" l "
                        + "JOIN campuscore_auth.\"User\" u ON u.\"id\" = l.\"userId\" "
                        + "JOIN academic.\"Department\" d ON d.\"id\" = l.\"departmentId\" "
                        + "JOIN academic.\"Faculty\" f ON f.\"id\" = d.\"facultyId\" "
                        + "WHERE l.\"id\" = :lecturerId AND l.\"userId\" = :subject "
                        + "AND l.\"isActive\" = TRUE AND u.\"status\" = 'ACTIVE' "
                        + "AND d.\"isActive\" = TRUE AND f.\"isActive\" = TRUE",
                new MapSqlParameterSource("lecturerId", lecturerId.trim())
                        .addValue("subject", actor.getSubject()), String.class);
        return rows.stream().findFirst().orElse(null);
    }

    /**
     * No-op for global admins and non-dean actors. A dean may only touch a topic
     * whose department sits inside their faculty — unless they personally
     * supervise it — and gets the same 404 the unscoped read path would return.
     */
    public static void requireTopicAccess(NamedParameterJdbcTemplate jdbc, Jwt actor, UUID topicId) {
        if (isGlobalAdmin(actor) || !isFacultyHead(actor) || topicId == null) {
            return;
        }
        String facultyId = facultyId(jdbc, actor);
        if (facultyId == null) {
            throw new DomainException(HttpStatus.FORBIDDEN, "FACULTY_PROFILE_REQUIRED",
                    "An active lecturer profile bound to the faculty head is required");
        }
        Integer inFaculty = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic t "
                        + "JOIN academic.\"Department\" d ON d.\"id\" = t.department_id "
                        + "WHERE t.id = :topicId AND d.\"facultyId\" = :facultyId",
                new MapSqlParameterSource("topicId", topicId).addValue("facultyId", facultyId),
                Integer.class);
        if (inFaculty != null && inFaculty > 0) {
            return;
        }
        String lecturerId = actor.getClaimAsString("lecturerId");
        Integer supervised = lecturerId == null ? 0 : jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor "
                        + "WHERE topic_id = :topicId AND lecturer_id = :lecturerId",
                new MapSqlParameterSource("topicId", topicId).addValue("lecturerId", lecturerId),
                Integer.class);
        if (supervised != null && supervised > 0) {
            return;
        }
        throw topicScopeFailure(jdbc, topicId);
    }

    /**
     * Governance outcomes are faculty-bound without the supervisor escape
     * {@link #requireTopicAccess} keeps for ordinary topic work: waiving or
     * restoring a counter-review, withdrawing or reinstating a topic, and
     * approving under the faculty minimum must come from a global admin or
     * the head of the faculty that owns the topic. Supervising it — even as
     * a dean — is never a waiver.
     */
    public static void requireTopicGovernanceScope(NamedParameterJdbcTemplate jdbc, Jwt actor, UUID topicId) {
        if (isGlobalAdmin(actor) || !isFacultyHead(actor) || topicId == null) {
            return;
        }
        String facultyId = facultyId(jdbc, actor);
        if (facultyId == null) {
            throw new DomainException(HttpStatus.FORBIDDEN, "FACULTY_PROFILE_REQUIRED",
                    "An active lecturer profile bound to the faculty head is required");
        }
        Integer inFaculty = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic t "
                        + "JOIN academic.\"Department\" d ON d.\"id\" = t.department_id "
                        + "WHERE t.id = :topicId AND d.\"facultyId\" = :facultyId",
                new MapSqlParameterSource("topicId", topicId).addValue("facultyId", facultyId),
                Integer.class);
        if (inFaculty == null || inFaculty == 0) {
            throw topicScopeFailure(jdbc, topicId);
        }
    }

    /**
     * Refusal classifier shared by both scope checks: a topic row whose
     * department resolves to no faculty cannot be governed by any dean, so
     * the failure must name the data problem — not pretend the row is gone
     * or merely out of scope. A global admin still reaches the row.
     */
    private static DomainException topicScopeFailure(NamedParameterJdbcTemplate jdbc, UUID topicId) {
        List<String> resolved = jdbc.queryForList(
                "SELECT f.\"id\" FROM thesis.thesis_topic t "
                        + "LEFT JOIN academic.\"Department\" d ON d.\"id\" = t.department_id "
                        + "LEFT JOIN academic.\"Faculty\" f ON f.\"id\" = d.\"facultyId\" "
                        + "WHERE t.id = :topicId",
                new MapSqlParameterSource("topicId", topicId), String.class);
        if (!resolved.isEmpty() && resolved.get(0) == null) {
            return new DomainException(HttpStatus.CONFLICT, "TOPIC_SCOPE_UNRESOLVABLE",
                    "The topic's department resolves to no faculty; only a global admin can act on it");
        }
        return new DomainException(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "Thesis topic not found");
    }

    /**
     * Read-side counterpart to {@link #requireTopicAccess}: true when the topic
     * belongs to the dean's active faculty or the dean personally supervises it.
     */
    public static boolean canAccessTopic(NamedParameterJdbcTemplate jdbc, Jwt actor, UUID topicId, String departmentId) {
        if (isGlobalAdmin(actor)) {
            return true;
        }
        if (!isFacultyHead(actor) || topicId == null) {
            return false;
        }
        String facultyId = facultyId(jdbc, actor);
        if (facultyId != null && departmentId != null && !departmentId.isBlank()) {
            Integer inFaculty = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM academic.\"Department\" WHERE \"id\" = :departmentId AND \"facultyId\" = :facultyId",
                    new MapSqlParameterSource("departmentId", departmentId).addValue("facultyId", facultyId),
                    Integer.class);
            if (inFaculty != null && inFaculty > 0) {
                return true;
            }
        }
        String lecturerId = actor == null ? null : actor.getClaimAsString("lecturerId");
        if (lecturerId == null || lecturerId.isBlank()) {
            return false;
        }
        Integer supervised = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor "
                        + "WHERE topic_id = :topicId AND lecturer_id = :lecturerId",
                new MapSqlParameterSource("topicId", topicId).addValue("lecturerId", lecturerId.trim()),
                Integer.class);
        return supervised != null && supervised > 0;
    }
}
