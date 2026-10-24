package io.campuscore.restfulapi.thesis.scope;

import io.campuscore.restfulapi.thesis.repository.ThesisScopeRepository;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

/** Resolves a department head's scope from their live, subject-bound profile. */
public final class ThesisDepartmentScope {

    private ThesisDepartmentScope() { }

    public static boolean isDepartmentHead(Jwt actor) {
        List<String> roles = actor == null ? null : actor.getClaimAsStringList("roles");
        return roles != null && roles.contains("TRUONG_BO_MON");
    }

    public static String departmentId(ThesisScopeRepository scope, Jwt actor) {
        if (!isDepartmentHead(actor) || actor.getSubject() == null) {
            return "";
        }
        String lecturerId = actor.getClaimAsString("lecturerId");
        if (lecturerId == null || lecturerId.isBlank()) {
            return "";
        }
        List<String> departments = scope.findHeadDepartmentIds(lecturerId.trim(), actor.getSubject());
        return departments.isEmpty() ? "" : departments.getFirst();
    }

    public static boolean owns(ThesisScopeRepository scope, Jwt actor, String departmentId) {
        String scopeDepartment = departmentId(scope, actor);
        return !scopeDepartment.isEmpty() && scopeDepartment.equals(departmentId);
    }
}
