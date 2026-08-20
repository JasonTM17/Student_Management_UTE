package io.campuscore.restfulapi.engagement.web;

import io.campuscore.restfulapi.engagement.service.AnnouncementReadService;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementListResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
            @RequestParam(defaultValue = "20") int limit) {
        return announcements.findForUser(
                values(jwt, "roles"),
                stringClaim(jwt, "studentId"),
                studentYear(jwt),
                stringClaim(jwt, "lecturerId"),
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
            @RequestParam(required = false) String priority) {
        return announcements.findAll(page, limit, semesterId, sectionId, priority);
    }

    private static List<String> values(Jwt jwt, String claimName) {
        if (jwt == null || !(jwt.getClaims().get(claimName) instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.toString().isBlank())
                .map(Object::toString)
                .toList();
    }

    private static String stringClaim(Jwt jwt, String claimName) {
        if (jwt == null) {
            return null;
        }
        Object value = jwt.getClaims().get(claimName);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isEmpty() ? null : text;
    }

    private static Integer studentYear(Jwt jwt) {
        if (jwt == null || !(jwt.getClaims().get("student") instanceof Map<?, ?> student)) {
            return null;
        }
        Object year = student.get("year");
        return year instanceof Number number ? number.intValue() : null;
    }
}
