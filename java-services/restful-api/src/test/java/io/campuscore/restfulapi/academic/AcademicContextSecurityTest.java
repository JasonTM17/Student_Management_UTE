package io.campuscore.restfulapi.academic;

import io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService;
import io.campuscore.restfulapi.academic.service.AcademicReadService;
import io.campuscore.restfulapi.academic.web.AcademicContextController;
import io.campuscore.restfulapi.academic.web.AcademicContextEnrollmentController;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class AcademicContextSecurityTest {

    private static final String HISTORICAL_DEFAULT_TOKEN = "academic-internal-token-12345";

    @Test
    void configuredSecretIsRequiredInsteadOfAcceptingHistoricalDefault() throws ReflectiveOperationException {
        String academicToken = resolvedServiceToken(
                AcademicContextController.class,
                AcademicReadService.class);
        String enrollmentToken = resolvedServiceToken(
                AcademicContextEnrollmentController.class,
                AcademicEnrollmentReadService.class);

        assertEquals("", academicToken);
        assertEquals("", enrollmentToken);

        AcademicContextController academic =
                new AcademicContextController(mock(AcademicReadService.class), academicToken);
        AcademicContextEnrollmentController enrollments =
                new AcademicContextEnrollmentController(mock(AcademicEnrollmentReadService.class), enrollmentToken);

        ResponseStatusException curriculumFailure = assertThrows(
                ResponseStatusException.class,
                () -> academic.getCurriculum(HISTORICAL_DEFAULT_TOKEN, "curriculum-cs"));
        ResponseStatusException enrollmentFailure = assertThrows(
                ResponseStatusException.class,
                () -> enrollments.getStudentEnrollments(HISTORICAL_DEFAULT_TOKEN, "student-1"));

        assertEquals(HttpStatus.FORBIDDEN, curriculumFailure.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, enrollmentFailure.getStatusCode());
    }

    private static String resolvedServiceToken(Class<?> controllerType, Class<?> serviceType)
            throws ReflectiveOperationException {
        Constructor<?> constructor = controllerType.getConstructor(serviceType, String.class);
        Value tokenValue = constructor.getParameters()[1].getAnnotation(Value.class);
        assertNotNull(tokenValue);
        assertFalse(tokenValue.value().contains(HISTORICAL_DEFAULT_TOKEN));
        return new MockEnvironment().resolveRequiredPlaceholders(tokenValue.value());
    }
}
