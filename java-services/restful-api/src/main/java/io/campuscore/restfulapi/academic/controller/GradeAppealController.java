package io.campuscore.restfulapi.academic.controller;

import io.campuscore.restfulapi.academic.dto.GradeAppealDtos.CreateRequest;
import io.campuscore.restfulapi.academic.dto.GradeAppealDtos.Eligibility;
import io.campuscore.restfulapi.academic.dto.GradeAppealDtos.Response;
import io.campuscore.restfulapi.academic.dto.GradeAppealDtos.ReviewRequest;
import io.campuscore.restfulapi.academic.dto.GradeAppealDtos.UnpublishRequest;
import io.campuscore.restfulapi.academic.dto.GradeAppealDtos.UnpublishResponse;
import io.campuscore.restfulapi.academic.service.GradeAppealService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Grade Appeals", description = "Student grade appeals, academic review, and administrative unpublishing")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1")
public class GradeAppealController {

    private final GradeAppealService appeals;

    public GradeAppealController(GradeAppealService appeals) {
        this.appeals = appeals;
    }

    @GetMapping("me/grade-appeals")
    @PreAuthorize("hasRole('STUDENT')")
    public List<Response> mine(@AuthenticationPrincipal Jwt jwt) {
        return appeals.listForStudent(jwt.getClaimAsString("studentId"));
    }

    @GetMapping("me/grade-appeals/eligibility")
    @PreAuthorize("hasRole('STUDENT')")
    public List<Eligibility> eligibility(@AuthenticationPrincipal Jwt jwt) {
        return appeals.eligibilityForStudent(jwt.getClaimAsString("studentId"));
    }

    @PostMapping("me/grade-appeals")
    @PreAuthorize("hasRole('STUDENT')")
    public Response submit(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateRequest request) {
        return appeals.submit(jwt.getClaimAsString("studentId"), jwt.getSubject(), request);
    }

    @PostMapping("me/grade-appeals/{id}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    public Response cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return appeals.cancel(id, jwt.getClaimAsString("studentId"), jwt.getSubject());
    }

    @GetMapping("lecturer/grade-appeals")
    @PreAuthorize("hasRole('LECTURER')")
    public List<Response> lecturerQueue(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false, defaultValue = "PENDING") String status) {
        return appeals.listForLecturer(jwt.getClaimAsString("lecturerId"), status);
    }

    @PostMapping("lecturer/grade-appeals/{id}/review")
    @PreAuthorize("hasRole('LECTURER')")
    public Response lecturerReview(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestBody ReviewRequest request) {
        return appeals.review(id, request, jwt.getSubject(), jwt.getClaimAsString("lecturerId"), false);
    }

    @GetMapping("admin/grade-appeals")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public List<Response> adminQueue(
            @RequestParam(required = false, defaultValue = "PENDING") String status) {
        return appeals.listForAdmin(status);
    }

    @PostMapping("admin/grade-appeals/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Response adminReview(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestBody ReviewRequest request) {
        return appeals.review(id, request, jwt.getSubject(), null, true);
    }

    @PostMapping("admin/sections/{sectionId}/grades/unpublish")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public UnpublishResponse unpublish(@AuthenticationPrincipal Jwt jwt, @PathVariable String sectionId,
            @RequestBody(required = false) UnpublishRequest request) {
        return appeals.unpublish(sectionId, request, jwt.getSubject());
    }
}
