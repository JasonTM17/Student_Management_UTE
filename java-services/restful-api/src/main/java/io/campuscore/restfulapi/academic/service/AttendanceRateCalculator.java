package io.campuscore.restfulapi.academic.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared attendance-rate definition for student and section summaries. */
final class AttendanceRateCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private AttendanceRateCalculator() {
    }

    /**
     * Returns the attended percentage rounded half-up to a whole number.
     * EXCUSED records are intentionally absent from these inputs and therefore
     * from both the numerator and denominator.
     */
    static int percentage(long present, long late, long absent) {
        BigDecimal attended = BigDecimal.valueOf(present).add(BigDecimal.valueOf(late));
        BigDecimal countable = attended.add(BigDecimal.valueOf(absent));
        if (countable.signum() <= 0) {
            return 0;
        }
        return attended.multiply(ONE_HUNDRED)
                .divide(countable, 0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
