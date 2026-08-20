package io.campuscore.restfulapi.engagement.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.campuscore.restfulapi.engagement.service.AnnouncementWriteService;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.CreateAnnouncementRequest;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.UpdateAnnouncementRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Feature-gated announcement creation candidate for the Java monolith. */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.engagement-write", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/announcements")
public class AnnouncementWriteController {

    private final AnnouncementWriteService announcements;

    public AnnouncementWriteController(AnnouncementWriteService announcements) {
        this.announcements = announcements;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JsonNode request) {
        return announcements.create(subject(jwt), CreateAnnouncementRequest.from(request));
    }

    @PutMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AnnouncementResponse update(
            @PathVariable String id,
            @RequestBody JsonNode request) {
        return announcements.update(id, UpdateAnnouncementRequest.from(request));
    }

    private static String subject(Jwt jwt) {
        if (jwt == null || !(jwt.getClaims().get("sub") instanceof String subject) || subject.isBlank()) {
            throw new AccessDeniedException("Not authenticated");
        }
        return subject;
    }
}
