package io.campuscore.restfulapi.thesis.model;

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

    /**
     * Whether governance may waive a topic's counter-review requirement in a
     * round of this type. Every shipped undergraduate level always requires a
     * counter-reviewer, so the waiver stays closed; a future postgraduate
     * round type opts in by returning true. The operator override
     * {@code thesis.governance.counter-review-waiver.enabled} and this
     * per-type answer meet in
     * {@link io.campuscore.restfulapi.thesis.service.ThesisGovernanceCapabilities#counterReviewWaiverAllowed},
     * the single decision point the write path and the read model share.
     */
    public boolean allowsCounterReviewWaiver() {
        return false;
    }

    public boolean requiresCouncilReportDate() {
        return this == TLCN || this == KLTN;
    }

    public boolean requiresDefenseDate() {
        return this == KLTN;
    }
}
