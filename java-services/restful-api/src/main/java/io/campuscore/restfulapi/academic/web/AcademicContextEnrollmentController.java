package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Internal academic-context enrollment reads guarded by X-Service-Token. */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.academic-context", name = "enabled", havingValue = "true")
@ConditionalOnBean(AcademicEnrollmentReadService.class)
@RequestMapping("/api/v1/internal/academic-context")
public class AcademicContextEnrollmentController {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final String INVALID_TOKEN_MESSAGE =
            "Internal endpoint requires a valid X-Service-Token header";

    private final AcademicEnrollmentReadService enrollments;
    private final String internalServiceToken;

    public AcademicContextEnrollmentController(
            AcademicEnrollmentReadService enrollments,
            @Value("${internal.service-token:}") String internalServiceToken) {
        this.enrollments = enrollments;
        this.internalServiceToken = internalServiceToken;
    }

    @GetMapping("students/{studentId}/enrollments")
    public List<EnrollmentResponse> getStudentEnrollments(
            @RequestHeader(value = SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @PathVariable String studentId) {
        requireServiceToken(serviceToken);
        return enrollments.findStudentEnrollments(studentId, null);
    }

    private void requireServiceToken(String suppliedToken) {
        if (suppliedToken == null || suppliedToken.isBlank() || !internalServiceToken.equals(suppliedToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, INVALID_TOKEN_MESSAGE);
        }
    }
}
