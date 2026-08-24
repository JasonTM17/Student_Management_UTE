package io.campuscore.restfulapi.registration;

import io.campuscore.restfulapi.registration.RegistrationDtos.EnrollmentPage;
import io.campuscore.restfulapi.registration.RegistrationDtos.EnrollmentRequest;
import io.campuscore.restfulapi.registration.RegistrationDtos.EligibilityView;
import io.campuscore.restfulapi.registration.RegistrationDtos.MutationResponse;
import io.campuscore.restfulapi.registration.RegistrationDtos.RoundPage;
import io.campuscore.restfulapi.registration.RegistrationDtos.RoundView;
import io.campuscore.restfulapi.registration.RegistrationDtos.SectionView;
import io.campuscore.restfulapi.registration.RegistrationDtos.SummaryView;
import io.campuscore.restfulapi.registration.RegistrationDtos.ValidationRequest;
import io.campuscore.restfulapi.registration.RegistrationDtos.ValidationResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Canonical student registration contract. Legacy aliases remain during migration. */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1")
public class RegistrationController {
    private final RegistrationService registration;

    public RegistrationController(RegistrationService registration) { this.registration = registration; }

    @GetMapping("registration/rounds")
    @PreAuthorize("hasRole('STUDENT')")
    public RoundPage rounds(@RequestParam(required = false) String semesterId,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        return registration.rounds(semesterId, cursor, limit);
    }

    @GetMapping("registration/rounds/{roundId}")
    @PreAuthorize("hasRole('STUDENT')")
    public RoundView round(@PathVariable String roundId) { return registration.round(roundId); }

    @GetMapping("registration/rounds/{roundId}/sections")
    @PreAuthorize("hasRole('STUDENT')")
    public List<SectionView> sections(@AuthenticationPrincipal Jwt jwt, @PathVariable String roundId) {
        return registration.sections(roundId, jwt.getClaimAsString("studentId"));
    }

    @GetMapping("me/registration/eligibility")
    @PreAuthorize("hasRole('STUDENT')")
    public EligibilityView eligibility(@AuthenticationPrincipal Jwt jwt, @RequestParam String roundId) {
        Number year = jwt.getClaim("studentYear");
        return registration.eligibility(jwt.getClaimAsString("studentId"), roundId, year == null ? null : year.intValue());
    }

    @GetMapping("me/registration/summary")
    @PreAuthorize("hasRole('STUDENT')")
    public SummaryView summary(@AuthenticationPrincipal Jwt jwt, @RequestParam String roundId) {
        return registration.summary(jwt.getClaimAsString("studentId"), roundId);
    }

    @GetMapping("me/enrollments")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<EnrollmentPage> enrollments(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String semesterId, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        EnrollmentPage result = registration.enrollments(jwt.getClaimAsString("studentId"), semesterId, cursor, limit);
        return ResponseEntity.ok().header("X-Next-Cursor", result.nextCursor() == null ? "" : result.nextCursor()).body(result);
    }

    @PostMapping("me/enrollments/validate")
    @PreAuthorize("hasRole('STUDENT')")
    public ValidationResponse validate(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ValidationRequest request) {
        return registration.validate(jwt.getClaimAsString("studentId"), new EnrollmentRequest(request.sectionId(), request.roundId()));
    }

    @PostMapping("me/enrollments")
    @PreAuthorize("hasRole('STUDENT')")
    public MutationResponse enroll(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody EnrollmentRequest request) {
        RegistrationService.MutationResult result = registration.enroll(jwt.getClaimAsString("studentId"), request, key);
        return new MutationResponse(result.enrollment(), result.replayed(), key.toString());
    }

    @DeleteMapping("me/enrollments/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> drop(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestHeader("Idempotency-Key") UUID key) {
        registration.drop(jwt.getClaimAsString("studentId"), id, key);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "me/registration/slip", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<byte[]> slip(@AuthenticationPrincipal Jwt jwt, @RequestParam String roundId) {
        byte[] pdf = registration.slip(jwt.getClaimAsString("studentId"), roundId);
        String hash = sha256(pdf);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("registration-slip.pdf").build());
        headers.set("X-Registration-Slip-Hash", hash);
        return new ResponseEntity<>(pdf, headers, org.springframework.http.HttpStatus.OK);
    }

    private static String sha256(byte[] bytes) { try { byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes); StringBuilder b = new StringBuilder(); for (byte value : digest) b.append(String.format("%02x", value)); return b.toString(); } catch (Exception e) { throw new IllegalStateException(e); } }
}
