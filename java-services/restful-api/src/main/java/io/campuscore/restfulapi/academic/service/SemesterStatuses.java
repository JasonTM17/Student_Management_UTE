package io.campuscore.restfulapi.academic.service;

import java.util.Locale;
import java.util.Set;
import io.campuscore.restfulapi.academic.model.SemesterLifecycleStatuses;

/** Semester lifecycle states shared by every academic write path. */
public final class SemesterStatuses {

    /** Semesters in which academic work (registration, grading, attendance) is still allowed. */
    public static final Set<String> OPEN = SemesterLifecycleStatuses.OPEN;

    /**
     * Lifecycle states a semester never leaves. The column is free-form
     * (VARCHAR(40)) and the admin catalog only writes CLOSED, but the set
     * covers the closed/completed/archived vocabulary the rest of the
     * codebase uses so a direct write cannot silently reopen. Compare a
     * stored value through {@link #normalize(String)} — a database TRIM
     * strips only plain spaces, so a terminal value padded with a tab, a
     * newline or a Unicode space would otherwise slip past the gate.
     */
    public static final Set<String> TERMINAL =
            Set.of("CLOSED", "COMPLETED", "ARCHIVED");

    /**
     * The comparison form lifecycle gates use for a stored status: strips
     * leading and trailing Unicode whitespace and ISO control characters —
     * including the no-break, figure and narrow no-break spaces
     * {@link String#trim()} leaves in place — then upper-cases with
     * {@link Locale#ROOT} so case variants refuse the same way.
     */
    public static String normalize(String status) {
        if (status == null) {
            return "";
        }
        int start = 0;
        int end = status.length();
        while (start < end && isPadding(status.charAt(start))) {
            start++;
        }
        while (end > start && isPadding(status.charAt(end - 1))) {
            end--;
        }
        return status.substring(start, end).toUpperCase(Locale.ROOT);
    }

    private static boolean isPadding(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c);
    }

    private SemesterStatuses() {
    }
}
