package io.campuscore.restfulapi.academic.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService;
import io.campuscore.restfulapi.academic.service.AcademicMutationService;
import io.campuscore.restfulapi.registration.RegistrationService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class AcademicMutationControllerContractTest {

    @Test
    void legacyDeleteUsesTheIdempotentRegistrationWriter() throws Exception {
        AcademicMutationService mutations = mock(AcademicMutationService.class);
        AcademicEnrollmentReadService enrollmentReads = mock(AcademicEnrollmentReadService.class);
        RegistrationService registration = mock(RegistrationService.class);
        UUID key = UUID.fromString("00000000-0000-4000-8000-000000000099");
        when(registration.dropAsAdmin("enrollment-1", key))
                .thenReturn(new RegistrationService.DropResult("enrollment-1", true));
        AcademicMutationController controller = new AcademicMutationController(mutations, enrollmentReads, registration);
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
}
