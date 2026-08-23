package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicMutationService;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicMutationDtos.EnrollRequest;
import io.campuscore.restfulapi.academic.web.AcademicMutationDtos.GradeUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("persistence")
@RequestMapping("/api/v1")
public class AcademicMutationController {

    private final AcademicMutationService mutations;

    public AcademicMutationController(AcademicMutationService mutations) {
        this.mutations = mutations;
    }

    @PostMapping("enrollments/enroll")
    @PreAuthorize("hasRole('STUDENT')")
    public EnrollmentResponse enroll(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EnrollRequest request) {
        return mutations.enroll(
                jwt.getClaimAsString("studentId"),
                request.sectionId(),
                jwt.getClaimAsStringList("roles"));
    }

    @PostMapping("enrollments/{id}/drop")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, String> drop(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        mutations.drop(id, jwt.getClaimAsString("studentId"), jwt.getClaimAsStringList("roles"));
        return Map.of("message", "Enrollment dropped successfully");
    }

    @DeleteMapping("enrollments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Map<String, String> deleteEnrollment(@PathVariable String id) {
        mutations.deleteEnrollment(id);
        return Map.of("message", "Enrollment deleted successfully");
    }

    @GetMapping(value = "enrollments/export/csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String exportEnrollments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String semesterId,
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String courseId) {
        return mutations.exportEnrollments(status, semesterId, studentId, courseId);
    }

    @PutMapping("sections/{sectionId}/grades")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, String> updateGrades(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String sectionId,
            @Valid @RequestBody GradeUpdateRequest request) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        mutations.updateGrades(sectionId, jwt.getClaimAsString("lecturerId"), isAdmin(roles), request.grades());
        return Map.of("message", "Grades saved as draft");
    }

    @PostMapping("sections/{sectionId}/grades/publish")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, String> publishGrades(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String sectionId) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        mutations.publishGrades(sectionId, jwt.getClaimAsString("lecturerId"), isAdmin(roles));
        return Map.of("message", "Grades published successfully");
    }

    private static boolean isAdmin(List<String> roles) {
        return roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN"));
    }
}
