package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.service.ThesisGroupReadService;
import io.campuscore.restfulapi.thesis.web.ThesisGroupReadDtos.GroupResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("persistence")
@RequestMapping("/api/v1/thesis/groups")
public class ThesisGroupReadController {

    private final ThesisGroupReadService groups;

    public ThesisGroupReadController(ThesisGroupReadService groups) {
        this.groups = groups;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','SUPER_ADMIN')")
    public List<GroupResponse> list(
            @RequestParam UUID roundId,
            @AuthenticationPrincipal Jwt actor) {
        return groups.list(roundId, roles(actor), actor.getClaimAsString("studentId"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','SUPER_ADMIN')")
    public GroupResponse get(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return groups.get(id, roles(actor), actor.getClaimAsString("studentId"));
    }

    private static List<String> roles(Jwt actor) {
        List<String> roles = actor.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }
}
