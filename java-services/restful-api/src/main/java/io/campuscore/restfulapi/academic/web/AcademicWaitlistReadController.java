package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicWaitlistReadService;
import io.campuscore.restfulapi.academic.web.AcademicWaitlistReadDtos.WaitlistListResponse;
import io.campuscore.restfulapi.academic.web.AcademicWaitlistReadDtos.WaitlistResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feature-gated academic waitlist reads. Waitlist promote/remove mutations
 * remain owned by the legacy academic service in this wave.
 */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.academic-waitlist-read", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/waitlist")
public class AcademicWaitlistReadController {

    private final AcademicWaitlistReadService academic;

    public AcademicWaitlistReadController(AcademicWaitlistReadService academic) {
        this.academic = academic;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public WaitlistListResponse getWaitlist(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sectionId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit", "sectionId"));
        return academic.findWaitlist(page, limit, sectionId);
    }

    @GetMapping("my")
    @PreAuthorize("hasRole('STUDENT')")
    public List<WaitlistResponse> getMyWaitlist(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return academic.findStudentWaitlist(jwt.getClaimAsString("studentId"));
    }

    @GetMapping("section/{sectionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER')")
    public List<WaitlistResponse> getSectionWaitlist(
            @PathVariable String sectionId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return academic.findSectionWaitlist(sectionId);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER')")
    public WaitlistResponse getWaitlistEntry(@PathVariable String id) {
        return academic.findWaitlistEntry(id);
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
}
