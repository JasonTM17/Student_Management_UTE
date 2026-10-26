package io.campuscore.restfulapi.academic.service;

import java.math.BigDecimal;

/**
 * Advisory exam-eligibility signal. It is read-model vocabulary only: nothing
 * here ever blocks an exam, a registration, or a grade. A student is AT_RISK
 * when the section already counted at least {@code minSessions} countable
 * records (PRESENT/LATE/ABSENT — EXCUSED is excluded by
 * {@link AttendanceRateCalculator}) and the rate falls under {@code minRate};
 * with fewer countable records the signal is INSUFFICIENT_DATA rather than a
 * premature risk flag.
 */
final class AttendanceEligibility {

    enum Signal {
        OK,
        AT_RISK,
        INSUFFICIENT_DATA
    }

    private AttendanceEligibility() {
    }

    static Signal signal(long present, long late, long countableSessions, int minSessions, double minRate) {
        if (countableSessions < minSessions) {
            return Signal.INSUFFICIENT_DATA;
        }
        // Compare attended sessions against the exact decimal threshold. The
        // rounded whole-percent value is only for display and can mask a small
        // shortfall such as 35/44 at an 80% threshold.
        BigDecimal attended = BigDecimal.valueOf(present).add(BigDecimal.valueOf(late));
        BigDecimal required = BigDecimal.valueOf(countableSessions).multiply(BigDecimal.valueOf(minRate));
        return attended.compareTo(required) < 0
                ? Signal.AT_RISK
                : Signal.OK;
    }

    static boolean atRisk(long present, long late, long countableSessions, int minSessions, double minRate) {
        return signal(present, late, countableSessions, minSessions, minRate) == Signal.AT_RISK;
    }
}
