package io.campuscore.engagement.web;

import io.campuscore.engagement.api.EngagementDtos;
import io.campuscore.engagement.service.AnnouncementService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

    private final AnnouncementService announcements;

    public AnnouncementController(AnnouncementService announcements) {
        this.announcements = announcements;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Page<EngagementDtos.AnnouncementResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return announcements.list(PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public EngagementDtos.AnnouncementResponse create(
            @RequestBody EngagementDtos.CreateAnnouncementRequest request,
            Authentication authentication) {
        UUID actorId = extractUserId(authentication);
        return announcements.create(request, actorId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public EngagementDtos.AnnouncementResponse get(@PathVariable UUID id) {
        try {
            return announcements.get(id);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Update and delete are planned for a later phase");
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return UUID.fromString(jwt.getSubject());
    }
}
