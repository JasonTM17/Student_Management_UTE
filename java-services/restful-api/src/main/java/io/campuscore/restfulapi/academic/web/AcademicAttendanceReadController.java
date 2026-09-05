package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicAttendanceReadService;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.AttendanceListResponse;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.AttendanceResponse;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.SectionAttendanceSummaryResponse;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.StudentAttendanceSummaryResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/** Role-protected academic attendance query routes. */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/attendance")
public class AcademicAttendanceReadController {

    private final AcademicAttendanceReadService academic;

    public AcademicAttendanceReadController(AcademicAttendanceReadService academic) {
        this.academic = academic;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AttendanceListResponse getAttendance(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sectionId,
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String date,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit", "sectionId", "studentId", "date"));
        return academic.findAll(page, limit, sectionId, studentId, date);
    }

    @GetMapping("my")
    @PreAuthorize("hasRole('STUDENT')")
    public List<AttendanceResponse> getMyAttendance(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String sectionId,
            @RequestParam(required = false) String semesterId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("sectionId", "semesterId"));
        return academic.findStudentAttendance(jwt.getClaimAsString("studentId"), sectionId, semesterId);
    }

    @GetMapping("my/summary")
    @PreAuthorize("hasRole('STUDENT')")
    public List<StudentAttendanceSummaryResponse> getMyAttendanceSummary(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String semesterId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("semesterId"));
        return academic.findStudentAttendanceSummary(jwt.getClaimAsString("studentId"), semesterId);
    }

    @GetMapping("lecturer/my")
    @PreAuthorize("hasRole('LECTURER')")
    public List<AttendanceResponse> getMySectionAttendance(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String sectionId,
            @RequestParam(required = false) String date,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("sectionId", "date"));
        return academic.findLecturerAttendance(jwt.getClaimAsString("lecturerId"), sectionId, date);
    }

    @GetMapping("section/{sectionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER')")
    public List<AttendanceResponse> getSectionAttendance(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String sectionId,
            @RequestParam(required = false) String date,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("date"));
        return academic.findSectionAttendance(
                sectionId,
                date,
                jwt.getClaimAsStringList("roles"),
                jwt.getClaimAsString("lecturerId"));
    }

    @GetMapping("section/{sectionId}/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER')")
    public SectionAttendanceSummaryResponse getSectionAttendanceSummary(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String sectionId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return academic.findSectionAttendanceSummary(
                sectionId,
                jwt.getClaimAsStringList("roles"),
                jwt.getClaimAsString("lecturerId"));
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER', 'STUDENT')")
    public AttendanceResponse getAttendanceRecord(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        return academic.findOne(
                id,
                jwt.getClaimAsStringList("roles"),
                jwt.getClaimAsString("studentId"),
                jwt.getClaimAsString("lecturerId"));
    }

    private static void requireAllowedQuery(
            MultiValueMap<String, String> queryParameters,
            Set<String> allowed) {
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            if ("_cc_nocache".equals(entry.getKey())) {
                continue;
            }
            if (!allowed.contains(entry.getKey()) || entry.getValue().size() != 1) {
                throw new IllegalArgumentException("Unexpected or repeated query parameter: " + entry.getKey());
            }
        }
    }
}
