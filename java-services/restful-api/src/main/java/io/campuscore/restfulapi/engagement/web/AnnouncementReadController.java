package io.campuscore.restfulapi.engagement.web;

import io.campuscore.restfulapi.engagement.service.AnnouncementReadService;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementListResponse;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feature-gated announcement reads. Legacy mutation and realtime event routes
 * remain exclusively owned by the engagement service.
 */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.engagement-read", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/announcements")
public class AnnouncementReadController {

    private final AnnouncementReadService announcements;

    public AnnouncementReadController(AnnouncementReadService announcements) {
        this.announcements = announcements;
    }

    @GetMapping("my")
    public AnnouncementListResponse getMyAnnouncements(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        requireLegacyIdentity(jwt);
        List<String> roles = values(jwt, "roles");
        String studentId = stringClaim(jwt, "studentId");
        Integer studentYear = studentYear(jwt);
        String lecturerId = stringClaim(jwt, "lecturerId");
        requireProfileClaims(roles, studentId, studentYear, lecturerId);
        return announcements.findForUser(
                roles,
                studentId,
                studentYear,
                lecturerId,
                page,
                limit);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AnnouncementListResponse getAllAnnouncements(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String semesterId,
            @RequestParam(required = false) String sectionId,
            @RequestParam(required = false) String priority,
            @RequestParam MultiValueMap<String, String> queryParameters,
            @AuthenticationPrincipal Jwt jwt) {
        requireAllowedQuery(
                queryParameters,
                Set.of("page", "limit", "semesterId", "sectionId", "priority"));
        requireLegacyIdentity(jwt);
        return announcements.findAll(
                page,
                limit,
                legacyOptional(semesterId),
                legacyOptional(sectionId),
                priority);
    }

    private static void requireAllowedQuery(
            MultiValueMap<String, String> queryParameters,
            Set<String> allowed) {
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            if (!allowed.contains(entry.getKey()) || entry.getValue().size() != 1) {
                throw new IllegalArgumentException("Unexpected or repeated query parameter: " + entry.getKey());
            }
        }
    }

    private static void requireLegacyIdentity(Jwt jwt) {
        String subject = stringClaim(jwt, "sub");
        if (subject == null
                || subject.isBlank()
                || stringClaim(jwt, "email") == null) {
            throw new BadCredentialsException("Invalid JWT claims");
        }
    }

    private static void requireProfileClaims(
            List<String> roles,
            String studentId,
            Integer studentYear,
            String lecturerId) {
        if (roles.contains("STUDENT") && (studentId == null || studentYear == null)) {
            throw new AccessDeniedException("Student profile claims are required");
        }
        if (roles.contains("LECTURER") && lecturerId == null) {
            throw new AccessDeniedException("Lecturer profile claim is required");
        }
    }

    private static String legacyOptional(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static List<String> values(Jwt jwt, String claimName) {
        if (jwt == null || !(jwt.getClaims().get(claimName) instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private static String stringClaim(Jwt jwt, String claimName) {
        if (jwt == null) {
            return null;
        }
        Object value = jwt.getClaims().get(claimName);
        if (!(value instanceof String text)) {
            return null;
        }
        return text.isEmpty() ? null : text;
    }

    private static Integer studentYear(Jwt jwt) {
        if (jwt == null || !(jwt.getClaims().get("student") instanceof Map<?, ?> student)) {
            return null;
        }
        Object year = student.get("year");
        if (year instanceof Byte || year instanceof Short || year instanceof Integer) {
            return ((Number) year).intValue();
        }
        if (year instanceof Long longYear
                && longYear >= Integer.MIN_VALUE
                && longYear <= Integer.MAX_VALUE) {
            return longYear.intValue();
        }
        if (year instanceof BigInteger bigYear
                && bigYear.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                && bigYear.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
            return bigYear.intValue();
        }
        return null;
    }
}
