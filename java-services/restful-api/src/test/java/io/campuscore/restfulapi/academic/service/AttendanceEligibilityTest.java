package io.campuscore.restfulapi.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AttendanceEligibilityTest {

    @Test
    void flagsTheRawRateBelowTheThresholdWhileKeepingTheRoundedDisplayRate() {
        int displayRate = AttendanceRateCalculator.percentage(35, 0, 9);

        assertThat(displayRate).isEqualTo(80);
        assertThat(AttendanceEligibility.signal(35, 0, 44, 5, 0.80))
                .isEqualTo(AttendanceEligibility.Signal.AT_RISK);
    }

    @Test
    void acceptsAnExactThresholdRate() {
        int displayRate = AttendanceRateCalculator.percentage(40, 0, 10);

        assertThat(displayRate).isEqualTo(80);
        assertThat(AttendanceEligibility.signal(40, 0, 50, 5, 0.80))
                .isEqualTo(AttendanceEligibility.Signal.OK);
    }
}
