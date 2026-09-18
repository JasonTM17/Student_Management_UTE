package io.campuscore.restfulapi.thesis.domain;

/**
 * Round kinds fixed by the course brief: course topics, undergraduate
 * research, specialization theses, and graduation theses. The GVPB grading
 * deadline is only meaningful for TLCN/KLTN rounds; the report date is used
 * by TLCN/KLTN and the defense date only by KLTN.
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
        return this == TLCN || this == KLTN;
    }

    public boolean requiresDefenseDate() {
        return this == KLTN;
    }
}
