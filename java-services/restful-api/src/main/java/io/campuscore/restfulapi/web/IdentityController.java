package io.campuscore.restfulapi.web;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Verifies that the future modules can consume the frozen identity claims. */
@RestController
@RequestMapping("/api/v1/me")
public class IdentityController {

    @GetMapping
    public IdentityResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        return new IdentityResponse(
                jwt.getSubject(),
                values(jwt.getClaims().get("roles")),
                values(jwt.getClaims().get("permissions")),
                stringClaim(jwt, "studentId"),
                stringClaim(jwt, "lecturerId"));
    }

    private List<String> values(Object claim) {
        if (!(claim instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .filter(value -> value != null && !value.toString().isBlank())
                .map(Object::toString)
                .toList();
    }

    private String stringClaim(Jwt jwt, String name) {
        Object value = jwt.getClaims().get(name);
        return value == null ? null : value.toString();
    }

    public record IdentityResponse(
            String subject,
            List<String> roles,
            List<String> permissions,
            String studentId,
            String lecturerId) {
    }
}
