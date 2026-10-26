package io.campuscore.restfulapi.academic.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Letter-grade scale shared by the transcript GPA conversion and the
 * prerequisite minimum-grade ranking.
 */
public final class LetterGrades {

    /**
     * Every letter the system can hold, strongest first. The 10-point
     * assignment only emits A+, A, B+, B, C+, C, D+, D and F; the minus rows
     * exist so legacy records carrying those letters still rank and render.
     */
    public static final List<String> SCALE = List.of(
            "A+", "A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D+", "D", "D-", "F");

    // The 4.0 conversion follows the official HCMUTE table for the 10-point
    // scale (A+/A=4.0, B+=3.5, B=3.0, C+=2.5, C=2.0, D+=1.5, D=1.0, F=0).
    private static final Map<String, BigDecimal> POINTS = Map.ofEntries(
            Map.entry("A+", BigDecimal.valueOf(4.0)),
            Map.entry("A", BigDecimal.valueOf(4.0)),
            Map.entry("A-", BigDecimal.valueOf(3.7)),
            Map.entry("B+", BigDecimal.valueOf(3.5)),
            Map.entry("B", BigDecimal.valueOf(3.0)),
            Map.entry("B-", BigDecimal.valueOf(2.7)),
            Map.entry("C+", BigDecimal.valueOf(2.5)),
            Map.entry("C", BigDecimal.valueOf(2.0)),
            Map.entry("C-", BigDecimal.valueOf(1.7)),
            Map.entry("D+", BigDecimal.valueOf(1.5)),
            Map.entry("D", BigDecimal.valueOf(1.0)),
            Map.entry("D-", BigDecimal.valueOf(0.7)),
            Map.entry("F", BigDecimal.ZERO));

    /** GPA points for a letter; null for values outside the scale (W, DRAFT). */
    public static BigDecimal points(String letterGrade) {
        return letterGrade == null ? null : POINTS.get(letterGrade);
    }

    /**
     * Ordinal strength for minimum-grade floors: A+ ranks highest, F lowest,
     * and letters outside the scale have no rank.
     */
    public static Integer rank(String letterGrade) {
        int index = letterGrade == null ? -1 : SCALE.indexOf(letterGrade);
        return index < 0 ? null : SCALE.size() - 1 - index;
    }

    private LetterGrades() {
    }
}
