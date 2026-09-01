package io.campuscore.restfulapi.engagement.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.campuscore.restfulapi.engagement.service.AnnouncementWriteService;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementHistoryListResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.CreateAnnouncementRequest;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.DeleteAnnouncementResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.LifecycleRequest;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.UpdateAnnouncementRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.oauth2.jwt.Jwt;

/** Role-protected announcement mutation and governance routes. */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/announcements")
public class AnnouncementWriteController {

    private final AnnouncementWriteService announcements;

    public AnnouncementWriteController(AnnouncementWriteService announcements) {
        this.announcements = announcements;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AnnouncementResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JsonNode request) {
        return announcements.create(subject(jwt), actorLabel(jwt), CreateAnnouncementRequest.from(request));
    }

    @PutMapping("{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AnnouncementResponse update(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JsonNode request) {
        return announcements.update(subject(jwt), actorLabel(jwt), id, UpdateAnnouncementRequest.from(request));
    }

    @GetMapping("{id}/history")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AnnouncementHistoryListResponse history(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters,
            @AuthenticationPrincipal Jwt jwt) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        subject(jwt);
        return announcements.history(id, page, limit);
    }

    @PostMapping("{id}/archive")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AnnouncementResponse archive(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JsonNode request) {
        return announcements.archive(subject(jwt), actorLabel(jwt), id, LifecycleRequest.from(request));
    }

    @PostMapping("{id}/restore")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AnnouncementResponse restore(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JsonNode request) {
        return announcements.restore(subject(jwt), actorLabel(jwt), id, LifecycleRequest.from(request));
    }

    /**
     * Kept for clients that still call DELETE. It now archives safely and leaves
     * the audit trail intact instead of removing the row.
     */
    @DeleteMapping("{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public DeleteAnnouncementResponse delete(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        return announcements.delete(subject(jwt), actorLabel(jwt), id);
    }

    private static void requireAllowedQuery(
            MultiValueMap<String, String> queryParameters,
            Set<String> allowed) {
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            if (!allowed.contains(entry.getKey()) || entry.getValue().size() != 1) {
                throw new IllegalArgumentException(
                        "Unexpected or repeated query parameter: " + entry.getKey());
            }
        }
    }

    private static String subject(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new AccessDeniedException("Not authenticated");
        }
        return jwt.getSubject();
    }

    /**
     * Keep the immutable actor id for authorization/audit joins while exposing a
     * useful human label in the Admin history UI. Claims are optional because
     * older tokens only carry a subject and email.
     */
    private static String actorLabel(Jwt jwt) {
        String firstName = claimAsString(jwt, "firstName");
        String lastName = claimAsString(jwt, "lastName");
        String fullName = (firstName + " " + lastName).trim();
        String email = claimAsString(jwt, "email");
        if (!fullName.isBlank()) {
            return email.isBlank() ? fullName : fullName + " · " + email;
        }
        return email.isBlank() ? subject(jwt) : email;
    }

    private static String claimAsString(Jwt jwt, String claim) {
        if (jwt == null) {
            return "";
        }
        Object value = jwt.getClaims().get(claim);
        return value == null ? "" : value.toString().trim();
    }
}
