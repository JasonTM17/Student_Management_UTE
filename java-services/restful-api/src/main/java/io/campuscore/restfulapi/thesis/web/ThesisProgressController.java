package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.service.ThesisProgressService;
import io.campuscore.restfulapi.thesis.web.ThesisProgressDtos.ProgressResponse;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("persistence")
@RequestMapping("/api/v1/thesis/me")
public class ThesisProgressController {

    private final ThesisProgressService progress;

    public ThesisProgressController(ThesisProgressService progress) {
        this.progress = progress;
    }

    @GetMapping("/progress")
    @PreAuthorize("hasRole('STUDENT')")
    public ProgressResponse get(@RequestParam UUID roundId, @AuthenticationPrincipal Jwt actor) {
        return progress.get(roundId, actor);
    }
}
