package io.campuscore.restfulapi.thesis.assistant;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Single authority for the assistant's civil calendar. The daily quota is
 * advertised as a "day" to students, so it must bucket by the campus's wall
 * clock, not UTC (which resets at 07:00 ICT). Both the turn ledger and the
 * personal-context advisor read this constant so the two can never drift.
 */
public final class AssistantTimezone {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private AssistantTimezone() {
    }

    /** ICT calendar date for an instant — the quota bucket key. */
    public static LocalDate bucketDateAt(Instant instant) {
        return LocalDate.ofInstant(instant, ZONE);
    }

    /** Current ICT calendar date. */
    public static LocalDate currentBucketDate() {
        return LocalDate.now(ZONE);
    }
}
