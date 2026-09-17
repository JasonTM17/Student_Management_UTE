package io.campuscore.restfulapi.academic.registration;

import io.campuscore.restfulapi.academic.registration.RegistrationDtos.CatalogSectionResponse;
import io.campuscore.restfulapi.academic.registration.RegistrationDtos.EligibilityResponse;
import io.campuscore.restfulapi.academic.registration.RegistrationDtos.RoundResponse;
import io.campuscore.restfulapi.academic.registration.RegistrationDtos.SummaryResponse;
import io.campuscore.restfulapi.academic.registration.RegistrationService.SlipPayload;
import io.campuscore.restfulapi.academic.registration.CreditLimitApplicationDtos.CreateRequest;
import io.campuscore.restfulapi.academic.registration.CreditLimitApplicationDtos.Response;
import io.campuscore.restfulapi.academic.registration.CreditLimitApplicationDtos.ReviewRequest;
import io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicMutationDtos.EnrollRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("persistence")
@RequestMapping("/api/v1")
public class RegistrationController {

    private final RegistrationService registration;
    private final AcademicEnrollmentReadService enrollments;
    private final CreditLimitApplicationService creditLimitApplications;

    public RegistrationController(
            RegistrationService registration,
            AcademicEnrollmentReadService enrollments,
            CreditLimitApplicationService creditLimitApplications) {
        this.registration = registration;
        this.enrollments = enrollments;
        this.creditLimitApplications = creditLimitApplications;
    }

    @GetMapping("registration/rounds")
    @PreAuthorize("isAuthenticated()")
    public List<RoundResponse> rounds(@RequestParam(required = false) String semesterId) {
        return registration.listRounds(semesterId);
    }

    @GetMapping("me/registration/eligibility")
    @PreAuthorize("hasRole('STUDENT')")
    public EligibilityResponse eligibility(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String semesterId,
            @RequestParam(required = false) String roundId) {
        return registration.eligibility(jwt.getClaimAsString("studentId"), semesterId, roundId);
    }

    @GetMapping("me/registration/sections")
    @PreAuthorize("hasRole('STUDENT')")
    public List<CatalogSectionResponse> sections(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String semesterId,
            @RequestParam(required = false) String roundId) {
        return registration.catalog(jwt.getClaimAsString("studentId"), semesterId, roundId);
    }

    @GetMapping("me/registration/summary")
    @PreAuthorize("hasRole('STUDENT')")
    public SummaryResponse summary(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String semesterId) {
        return registration.summary(jwt.getClaimAsString("studentId"), semesterId);
    }

    @GetMapping("me/registration/credit-limit-application")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Response> creditLimitApplication(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String roundId) {
        Response application = creditLimitApplications.findForStudent(
                jwt.getClaimAsString("studentId"), roundId);
        return application == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(application);
    }

    @PostMapping("me/registration/credit-limit-applications")
    @PreAuthorize("hasRole('STUDENT')")
    public Response submitCreditLimitApplication(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateRequest request) {
        return creditLimitApplications.submit(
                jwt.getClaimAsString("studentId"), request.roundId(), request.reason());
    }

    @GetMapping("admin/registration/credit-limit-applications")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public List<Response> creditLimitApplications(
            @RequestParam(required = false, defaultValue = "PENDING") String status) {
        return creditLimitApplications.list(status);
    }

    @PostMapping("admin/registration/credit-limit-applications/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Response reviewCreditLimitApplication(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody ReviewRequest request) {
        return creditLimitApplications.review(id, request.decision(), jwt.getSubject(), request.note());
    }

    @GetMapping("me/registration/slip")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<byte[]> slip(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String semesterId) {
        SlipPayload payload = registration.slip(jwt.getClaimAsString("studentId"), semesterId);
        return ResponseEntity.ok()
                .header("X-Content-SHA256", payload.sha256())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("registration-slip.pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(payload.payload());
    }

    @GetMapping("me/enrollments")
    @PreAuthorize("hasRole('STUDENT')")
    public List<EnrollmentResponse> myEnrollments(@AuthenticationPrincipal Jwt jwt) {
        return enrollments.findStudentEnrollments(jwt.getClaimAsString("studentId"), null);
    }

    @PostMapping("me/enrollments")
    @PreAuthorize("hasRole('STUDENT')")
    public EnrollmentResponse enroll(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody EnrollRequest request) {
        return registration.enroll(
                jwt.getClaimAsString("studentId"),
                request.sectionId(),
                jwt.getClaimAsStringList("roles"),
                idempotencyKey);
    }

    @PostMapping("me/enrollments/{id}/drop")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, String> drop(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        registration.drop(id, jwt.getClaimAsString("studentId"), jwt.getClaimAsStringList("roles"), idempotencyKey);
        return Map.of("message", "Enrollment dropped successfully");
    }
}
