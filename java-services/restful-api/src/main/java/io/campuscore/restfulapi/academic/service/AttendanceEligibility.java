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

    static Signal signal(int attendanceRate, long countableSessions, int minSessions, double minRate) {
        if (countableSessions < minSessions) {
            return Signal.INSUFFICIENT_DATA;
        }
        // BigDecimal comparison: a student sitting exactly on the threshold is
        // OK, never rounded into risk by floating-point representation error.
        BigDecimal threshold = BigDecimal.valueOf(minRate).multiply(BigDecimal.valueOf(100));
        return BigDecimal.valueOf(attendanceRate).compareTo(threshold) < 0
                ? Signal.AT_RISK
                : Signal.OK;
    }

    static boolean atRisk(int attendanceRate, long countableSessions, int minSessions, double minRate) {
        return signal(attendanceRate, countableSessions, minSessions, minRate) == Signal.AT_RISK;
    }
}
