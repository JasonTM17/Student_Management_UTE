package io.campuscore.restfulapi.academic.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** Read and write use the campus calendar day, stored as that date's UTC midnight. */
public final class AttendanceDay {
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private AttendanceDay() { }

    public static Instant parse(String value) {
        LocalDate date;
        try {
            date = Instant.parse(value).atZone(CAMPUS_ZONE).toLocalDate();
        } catch (DateTimeParseException dateOnly) {
            date = LocalDate.parse(value);
        }
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
