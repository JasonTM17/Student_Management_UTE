package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentListResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.GradeItemResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.GradeSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.StudentGradeSectionRow;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.StudentGradesByEnrollmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.TranscriptResponse;
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

/** Student, lecturer and administrator enrollment and grade read routes. */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1")
public class AcademicEnrollmentReadController {

    private final AcademicEnrollmentReadService academic;

    public AcademicEnrollmentReadController(AcademicEnrollmentReadService academic) {
        this.academic = academic;
    }

    @GetMapping("enrollments/my")
    @PreAuthorize("hasRole('STUDENT')")
    public List<EnrollmentResponse> getMyEnrollments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String semesterId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("semesterId"));
        return academic.findStudentEnrollments(jwt.getClaimAsString("studentId"), semesterId);
    }

    @GetMapping("enrollments/my/grades")
    @PreAuthorize("hasRole('STUDENT')")
    public List<GradeSummary> getMyGrades(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String semesterId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("semesterId"));
        return academic.findStudentGrades(jwt.getClaimAsString("studentId"), semesterId);
    }

    @GetMapping("enrollments/my/transcript")
    @PreAuthorize("hasRole('STUDENT')")
    public TranscriptResponse getMyTranscript(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return academic.findStudentTranscript(jwt.getClaimAsString("studentId"));
    }

    @GetMapping("enrollments/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public List<EnrollmentResponse> getStudentEnrollments(
            @PathVariable String studentId,
            @RequestParam(required = false) String semesterId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("semesterId"));
        return academic.findStudentEnrollments(studentId, semesterId);
    }

    @GetMapping("enrollments")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public EnrollmentListResponse getEnrollments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String semesterId,
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String sectionId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(
                queryParameters,
                Set.of("page", "limit", "status", "semesterId", "studentId", "courseId", "sectionId"));
        return academic.findEnrollments(page, limit, status, semesterId, studentId, courseId, sectionId);
    }

    @GetMapping("enrollments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'STUDENT')")
    public EnrollmentResponse getEnrollment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        return academic.findEnrollment(id, jwt.getClaimAsStringList("roles"), jwt.getClaimAsString("studentId"));
    }

    @GetMapping("grades/items/section/{sectionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER')")
    public List<GradeItemResponse> getGradeItemsBySection(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String sectionId) {
        return academic.findGradeItemsBySection(sectionId, jwt.getClaimAsStringList("roles"), jwt.getClaimAsString("lecturerId"));
    }

    @GetMapping("grades/items/lecturer/my")
    @PreAuthorize("hasRole('LECTURER')")
    public List<GradeItemResponse> getMyGradeItems(@AuthenticationPrincipal Jwt jwt) {
        return academic.findGradeItemsByLecturer(jwt.getClaimAsString("lecturerId"));
    }

    @GetMapping("grades/student-grades/section/{sectionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER')")
    public List<StudentGradeSectionRow> getStudentGradesBySection(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String sectionId) {
        return academic.findStudentGradesBySection(sectionId, jwt.getClaimAsStringList("roles"), jwt.getClaimAsString("lecturerId"));
    }

    @GetMapping("grades/student-grades/lecturer/my")
    @PreAuthorize("hasRole('LECTURER')")
    public List<StudentGradeSectionRow> getMyStudentGrades(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String sectionId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("sectionId"));
        return academic.findStudentGradesByLecturer(jwt.getClaimAsString("lecturerId"), sectionId);
    }

    @GetMapping("grades/student-grades/enrollment/{enrollmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER')")
    public StudentGradesByEnrollmentResponse getStudentGradesByEnrollment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String enrollmentId) {
        return academic.findStudentGradesByEnrollment(enrollmentId, jwt.getClaimAsStringList("roles"), jwt.getClaimAsString("lecturerId"));
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
