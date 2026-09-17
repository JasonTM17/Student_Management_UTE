package io.campuscore.restfulapi.academic.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.academic.service.AcademicMutationService;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicMutationDtos.EnrollRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * LEC-P2-10: the deprecated enroll/drop endpoints sent `Sunset: true`, which
 * is not an HTTP-date and is therefore ignored by conforming clients
 * (RFC 8594). The header must be gone, not mangled.
 */
class AcademicMutationControllerHeaderTest {

    @Test
    void deprecatedEnrollAndDropDoNotAdvertiseAnInvalidSunsetDate() {
        AcademicMutationService mutations = Mockito.mock(AcademicMutationService.class);
        when(mutations.enroll(any(), any(), any(), any())).thenReturn(null);
        AcademicMutationController controller = new AcademicMutationController(mutations);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("studentId", "student-1")
                .claim("roles", List.of("STUDENT"))
                .build();

        ResponseEntity<EnrollmentResponse> enrollResponse =
                controller.enroll(jwt, "idem-enroll", new EnrollRequest("section-1"));
        assertThat(enrollResponse.getHeaders().getFirst("Sunset"))
                .as("Sunset must not carry a non-HTTP-date value")
                .isNull();
        assertThat(enrollResponse.getHeaders().getFirst("Deprecation")).isEqualTo("true");

        ResponseEntity<Map<String, String>> dropResponse = controller.drop(jwt, "enrollment-1", "idem-drop");
        assertThat(dropResponse.getHeaders().getFirst("Sunset")).isNull();
        assertThat(dropResponse.getHeaders().getFirst("Deprecation")).isEqualTo("true");
    }
}
