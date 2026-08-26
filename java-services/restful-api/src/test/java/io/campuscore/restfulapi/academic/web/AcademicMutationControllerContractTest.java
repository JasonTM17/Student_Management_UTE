package io.campuscore.restfulapi.academic.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.academic.service.AcademicMutationService;
import io.campuscore.restfulapi.registration.RegistrationDtos;
import io.campuscore.restfulapi.registration.RegistrationService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;

class AcademicMutationControllerContractTest {

    @Test
    void legacyDeleteUsesTheIdempotentRegistrationWriter() throws Exception {
        AcademicMutationService mutations = mock(AcademicMutationService.class);
        RegistrationService registration = mock(RegistrationService.class);
        UUID key = UUID.fromString("00000000-0000-4000-8000-000000000099");
        when(registration.dropAsAdmin("enrollment-1", key))
                .thenReturn(new RegistrationService.DropResult("enrollment-1", true));
        AcademicMutationController controller = new AcademicMutationController(mutations, registration);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, String> result = controller.deleteEnrollment("enrollment-1", key, response);

        assertThat(result).containsEntry("message", "Enrollment deleted successfully");
        assertThat(response.getHeader("Deprecation")).isEqualTo("true");
        assertThat(response.getHeader("Link"))
                .isEqualTo("</api/v1/me/enrollments/enrollment-1>; rel=\"successor-version\"");
        assertThat(response.getHeader("Idempotency-Replayed")).isEqualTo("true");
        verify(registration).dropAsAdmin("enrollment-1", key);
        verifyNoInteractions(mutations);
    }

    @Test
    void legacyEnrollReplayReturnsTheStableWriterPayload() {
        AcademicMutationService mutations = mock(AcademicMutationService.class);
        RegistrationService registration = mock(RegistrationService.class);
        UUID key = UUID.fromString("00000000-0000-4000-8000-000000000100");
        RegistrationDtos.EnrollmentView stable = new RegistrationDtos.EnrollmentView(
                "enrollment-1", "section-1", "round-1", "ENROLLED",
                Instant.parse("2026-08-26T00:00:00Z"),
                new RegistrationDtos.SectionView(
                        "section-1", "course-1", "CS101", "Distributed systems", 3,
                        "A", "Lecturer", "A101", 30, 1, 29, "OPEN", true,
                        List.of(), List.of()));
        when(registration.enrollBySection("student-1", "section-1", key))
                .thenReturn(new RegistrationService.MutationResult(stable, false))
                .thenReturn(new RegistrationService.MutationResult(stable, true));
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .claim("studentId", "student-1")
                .claim("roles", List.of("STUDENT"))
                .build();
        AcademicMutationController controller = new AcademicMutationController(mutations, registration);

        RegistrationDtos.MutationResponse first = controller.enroll(
                jwt, new AcademicMutationDtos.EnrollRequest("section-1"), key, new MockHttpServletResponse());
        RegistrationDtos.MutationResponse replay = controller.enroll(
                jwt, new AcademicMutationDtos.EnrollRequest("section-1"), key, new MockHttpServletResponse());

        assertThat(first.enrollment()).isEqualTo(stable);
        assertThat(first.replayed()).isFalse();
        assertThat(replay.enrollment()).isEqualTo(stable);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.clientRequestId()).isEqualTo(key.toString());
        verify(registration, org.mockito.Mockito.times(2)).enrollBySection("student-1", "section-1", key);
        verifyNoInteractions(mutations);
    }
}
