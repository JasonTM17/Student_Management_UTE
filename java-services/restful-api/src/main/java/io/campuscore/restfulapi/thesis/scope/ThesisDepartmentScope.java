package io.campuscore.restfulapi.thesis.scope;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;

/** Resolves a department head's scope from their live, subject-bound profile. */
public final class ThesisDepartmentScope {

    private ThesisDepartmentScope() { }

    public static boolean isDepartmentHead(Jwt actor) {
        List<String> roles = actor == null ? null : actor.getClaimAsStringList("roles");
        return roles != null && roles.contains("TRUONG_BO_MON");
    }

    public static String departmentId(NamedParameterJdbcTemplate jdbc, Jwt actor) {
        if (!isDepartmentHead(actor) || actor.getSubject() == null) {
            return "";
        }
        String lecturerId = actor.getClaimAsString("lecturerId");
        if (lecturerId == null || lecturerId.isBlank()) {
            return "";
        }
        List<String> departments = jdbc.queryForList(
                "SELECT l.\"departmentId\" FROM campuscore_auth.\"Lecturer\" l "
                        + "JOIN campuscore_auth.\"User\" u ON u.\"id\" = l.\"userId\" "
                        + "JOIN academic.\"Department\" d ON d.\"id\" = l.\"departmentId\" "
                        + "WHERE l.\"id\" = :lecturerId AND l.\"userId\" = :subject "
                        + "AND l.\"isActive\" = TRUE AND u.\"status\" = 'ACTIVE' AND d.\"isActive\" = TRUE",
                new MapSqlParameterSource("lecturerId", lecturerId.trim())
                        .addValue("subject", actor.getSubject()), String.class);
        return departments.isEmpty() ? "" : departments.getFirst();
    }

    public static boolean owns(NamedParameterJdbcTemplate jdbc, Jwt actor, String departmentId) {
        String scope = departmentId(jdbc, actor);
        return !scope.isEmpty() && scope.equals(departmentId);
    }
}
