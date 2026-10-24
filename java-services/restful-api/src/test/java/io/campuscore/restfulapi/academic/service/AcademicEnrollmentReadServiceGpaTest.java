package io.campuscore.restfulapi.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AcademicEnrollmentReadServiceGpaTest {

    @Test
    void gpaRoundsExactHalfCentValuesUpToTwoDecimals() throws Exception {
        Method gpa = AcademicEnrollmentReadService.class.getDeclaredMethod("gpa", BigDecimal.class, int.class);
        gpa.setAccessible(true);

        assertThat(gpa.invoke(null, new BigDecimal("3135"), 1000)).isEqualTo(new BigDecimal("3.14"));
        assertThat(gpa.invoke(null, new BigDecimal("3145"), 1000)).isEqualTo(new BigDecimal("3.15"));
    }
}
