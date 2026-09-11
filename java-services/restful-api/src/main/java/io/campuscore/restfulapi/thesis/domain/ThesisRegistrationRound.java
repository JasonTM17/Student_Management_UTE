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

    @Column(name = "report_date")
    private Instant reportDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RoundStatus status;

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
    public Instant getReportDate() { return reportDate; }
    public RoundStatus getStatus() { return status; }
}
