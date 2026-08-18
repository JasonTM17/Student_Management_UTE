package io.campuscore.thesis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "thesis_registration_round", schema = "academic")
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

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ThesisRegistrationRound() {
    }

    public ThesisRegistrationRound(
            String name,
            String thesisType,
            Instant registrationStart,
            Instant registrationEnd,
            Instant proposalPublishAt,
            Instant reportDate,
            Instant defenseDate) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.thesisType = thesisType;
        this.registrationStart = registrationStart;
        this.registrationEnd = registrationEnd;
        this.proposalPublishAt = proposalPublishAt;
        this.reportDate = reportDate;
        this.defenseDate = defenseDate;
        this.status = RoundStatus.DRAFT;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void openRegistration() {
        if (status != RoundStatus.DRAFT) {
            throw new IllegalStateException("Only draft rounds can open registration");
        }
        status = RoundStatus.REGISTRATION_OPEN;
    }

    public void publishProposals() {
        if (status != RoundStatus.REGISTRATION_CLOSED) {
            throw new IllegalStateException("Registration must be closed before publishing proposals");
        }
        status = RoundStatus.PROPOSALS_PUBLISHED;
    }

    public void closeRegistration() {
        if (status != RoundStatus.REGISTRATION_OPEN) {
            throw new IllegalStateException("Only open rounds can close registration");
        }
        status = RoundStatus.REGISTRATION_CLOSED;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getThesisType() {
        return thesisType;
    }

    public Instant getRegistrationStart() {
        return registrationStart;
    }

    public Instant getRegistrationEnd() {
        return registrationEnd;
    }

    public Instant getProposalPublishAt() {
        return proposalPublishAt;
    }

    public Instant getReportDate() {
        return reportDate;
    }

    public Instant getDefenseDate() {
        return defenseDate;
    }

    public RoundStatus getStatus() {
        return status;
    }
}
