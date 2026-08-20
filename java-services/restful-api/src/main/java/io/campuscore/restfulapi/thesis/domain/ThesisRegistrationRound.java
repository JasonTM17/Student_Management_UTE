package io.campuscore.restfulapi.thesis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Read model for the legacy-compatible thesis registration-round schema. */
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

    @Column(name = "report_date")
    private Instant reportDate;

    @Column(name = "defense_date")
    private Instant defenseDate;

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
    public Instant getReportDate() { return reportDate; }
    public Instant getDefenseDate() { return defenseDate; }
    public RoundStatus getStatus() { return status; }
}
