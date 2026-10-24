package io.campuscore.restfulapi.academic.registration.dto;

import static org.assertj.core.api.Assertions.assertThat;

import io.campuscore.restfulapi.academic.registration.dto.RegistrationDtos.SummaryResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationDtosSummaryTest {

    @Test
    void compatibilityConstructorDoesNotInventASemesterSpecificMinimum() {
        SummaryResponse summary = new SummaryResponse("round-1", 20, 3, 17, List.of("enrollment-1"));

        assertThat(summary.minCredits()).isZero();
        assertThat(summary.belowMinimum()).isFalse();
    }
}
