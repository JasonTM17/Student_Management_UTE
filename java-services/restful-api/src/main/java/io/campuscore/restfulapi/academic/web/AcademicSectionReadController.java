package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicSectionReadService;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.LecturerGradingSectionResponse;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.LecturerScheduleResponse;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.SectionGradesResponse;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.SectionListResponse;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.SectionResponse;
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
 * Feature-gated academic section reads. Section create/update/delete and grade
 * update/publish mutations remain owned by the legacy academic service.
 */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.academic-section-read", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/sections")
public class AcademicSectionReadController {

    private final AcademicSectionReadService academic;

    public AcademicSectionReadController(AcademicSectionReadService academic) {
        this.academic = academic;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public SectionListResponse getSections(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String semesterId,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String courseId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit", "semesterId", "departmentId", "courseId"));
        return academic.findSections(page, limit, semesterId, departmentId, courseId);
    }

    @GetMapping("my/schedule")
    @PreAuthorize("hasRole('LECTURER')")
    public List<LecturerScheduleResponse> getMySchedule(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String semesterId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("semesterId"));
        return academic.findLecturerSchedule(jwt.getClaimAsString("lecturerId"), semesterId);
    }

    @GetMapping("my/grading")
    @PreAuthorize("hasRole('LECTURER')")
    public List<LecturerGradingSectionResponse> getMyGradingSections(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String semesterId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("semesterId"));
        return academic.findLecturerGradingSections(jwt.getClaimAsString("lecturerId"), semesterId);
    }

    @GetMapping("{id}")
    @PreAuthorize("isAuthenticated()")
    public SectionResponse getSection(@PathVariable String id) {
        return academic.findSection(id);
    }

    @GetMapping("{id}/grades")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER')")
    public SectionGradesResponse getSectionGrades(@PathVariable String id) {
        return academic.findSectionGrades(id);
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
