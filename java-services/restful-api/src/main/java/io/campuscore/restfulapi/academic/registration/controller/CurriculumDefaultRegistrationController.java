package io.campuscore.restfulapi.academic.registration.controller;

import static io.campuscore.restfulapi.academic.registration.dto.CurriculumDefaultRegistrationDtos.*;

import io.campuscore.restfulapi.academic.registration.service.CurriculumDefaultRegistrationService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated student and Academic Affairs endpoints for curriculum registration plans. */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1")
public class CurriculumDefaultRegistrationController {
    private final CurriculumDefaultRegistrationService curriculumDefaults;

    public CurriculumDefaultRegistrationController(CurriculumDefaultRegistrationService curriculumDefaults) {
        this.curriculumDefaults = curriculumDefaults;
    }

    @GetMapping("me/registration/curriculum-plan")
    @PreAuthorize("hasRole('STUDENT')")
    public PlanResponse plan(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String semesterId,
            @RequestParam(required = false) Integer curriculumYear,
            @RequestParam(required = false) Integer curriculumSemester) {
        return curriculumDefaults.plan(jwt.getClaimAsString("studentId"), semesterId,
                curriculumYear, curriculumSemester);
    }

    @PostMapping("me/registration/curriculum-plan/register")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentRegistrationResponse register(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) StudentRegistrationRequest request) {
        return curriculumDefaults.registerStudent(jwt.getClaimAsString("studentId"),
                request == null ? null : request.semesterId(),
                request == null ? null : request.curriculumYear(),
                request == null ? null : request.curriculumSemester());
    }

    @PostMapping("admin/registration/curriculum-defaults/dry-run")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AdminRunResponse dryRun(@RequestBody AdminRunRequest request) {
        return curriculumDefaults.dryRun(request);
    }

    @PostMapping("admin/registration/curriculum-defaults/apply")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AdminRunResponse apply(@AuthenticationPrincipal Jwt jwt, @RequestBody AdminRunRequest request) {
        String actorLabel = jwt.getClaimAsString("email");
        return curriculumDefaults.apply(request, jwt.getSubject(),
                actorLabel == null || actorLabel.isBlank() ? jwt.getSubject() : actorLabel);
    }

    @GetMapping("admin/registration/curriculum-defaults/runs")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AdminRunList recentRuns() {
        return curriculumDefaults.recentRuns();
    }
}
