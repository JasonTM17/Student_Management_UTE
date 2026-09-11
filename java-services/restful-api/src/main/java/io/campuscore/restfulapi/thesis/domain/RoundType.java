package io.campuscore.restfulapi.thesis.domain;

/**
 * Round kinds fixed by the course brief: course topics, undergraduate
 * research, specialization theses, and graduation theses. The GVPB grading
 * deadline is only meaningful for TLCN/KLTN rounds; the council report date
 * only for KLTN.
 */
public enum RoundType {
    MON_HOC,
    NCKH,
    TLCN,
    KLTN;

    public boolean requiresGvpbDeadline() {
        return this == TLCN || this == KLTN;
    }

    public boolean requiresCouncilReportDate() {
        return this == KLTN;
    }
}
