package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.service.ThesisLecturerWorkloadService;
import io.campuscore.restfulapi.thesis.service.ThesisLecturerWorkloadService.LecturerWorkload;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lecturer-scoped thesis workload for the campus assistant (read-only, self-scoped). */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/thesis")
public class ThesisWorkloadController {

    private final ThesisLecturerWorkloadService workloadService;

    public ThesisWorkloadController(ThesisLecturerWorkloadService workloadService) {
        this.workloadService = workloadService;
    }

    @GetMapping("/me/workload")
    @PreAuthorize("hasRole('LECTURER')")
    public LecturerWorkload myWorkload(@AuthenticationPrincipal Jwt actor) {
        String lecturerId = actor == null ? null : actor.getClaimAsString("lecturerId");
        if (!StringUtils.hasText(lecturerId)) {
            return new LecturerWorkload(java.util.List.of(), java.util.List.of());
        }
        return workloadService.workload(lecturerId);
    }
}
