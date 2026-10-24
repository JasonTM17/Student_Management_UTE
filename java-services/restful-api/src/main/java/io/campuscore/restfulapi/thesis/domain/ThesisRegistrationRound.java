package io.campuscore.restfulapi.thesis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Domain model for a thesis registration round. */
@Entity
@Table(name = "thesis_registration_round", schema = "thesis")
public class ThesisRegistrationRound {

    @Id
    private UUID id;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "thesis_type", nullable = false, length = 40)
    private String thesisType;

    @Column(name = "registration_start", nullable = false)
    private Instant registrationStart;

    @Column(name = "registration_end", nullable = false)
    private Instant registrationEnd;

    @Column(name = "proposal_publish_at")
    private Instant proposalPublishAt;

    /** Lecturer topic-submission window (phase one of the brief). */
    @Column(name = "lecturer_submit_start", nullable = false)
    private Instant lecturerSubmitStart;

    @Column(name = "lecturer_submit_end", nullable = false)
    private Instant lecturerSubmitEnd;

    /** GVPB grading deadline; required for TLCN/KLTN rounds, null otherwise. */
    @Column(name = "gvpb_deadline")
    private Instant gvpbDeadline;

    /**
     * Optional report-submission deadline override. Written only by the
     * report-deadline escape hatch; when set it replaces {@code gvpbDeadline}
     * as the report freeze bound without reopening the GVPB scoring window.
     */
    @Column(name = "report_deadline")
    private Instant reportDeadline;

    @Column(name = "report_date")
    private Instant reportDate;

    @Column(name = "defense_date")
    private Instant defenseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RoundStatus status;

    /**
     * Status the round held when it was cancelled; only populated while the
     * round is CANCELLED. NULL marks a legacy cancellation — those restore to
     * DRAFT because no earlier status survives to replay.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_from_status", length = 32)
    private RoundStatus cancelledFromStatus;

    /**
     * Publication instant of the current result set. Deliberately preserved
     * through CLOSE/REOPEN so the retraction grace window always measures the
     * original publication; retract sets it back to NULL.
     */
    @Column(name = "results_published_at")
    private Instant resultsPublishedAt;

    protected ThesisRegistrationRound() {
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getThesisType() { return thesisType; }
    public Instant getRegistrationStart() { return registrationStart; }
    public Instant getRegistrationEnd() { return registrationEnd; }
    public Instant getProposalPublishAt() { return proposalPublishAt; }
    public Instant getLecturerSubmitStart() { return lecturerSubmitStart; }
    public Instant getLecturerSubmitEnd() { return lecturerSubmitEnd; }
    public Instant getGvpbDeadline() { return gvpbDeadline; }
    public Instant getReportDeadline() { return reportDeadline; }
    public Instant getReportDate() { return reportDate; }
    public Instant getDefenseDate() { return defenseDate; }
    public RoundStatus getStatus() { return status; }
    public RoundStatus getCancelledFromStatus() { return cancelledFromStatus; }
    public Instant getResultsPublishedAt() { return resultsPublishedAt; }
}
