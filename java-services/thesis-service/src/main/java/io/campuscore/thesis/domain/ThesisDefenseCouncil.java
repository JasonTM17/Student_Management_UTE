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
@Table(name = "thesis_defense_council", schema = "academic")
public class ThesisDefenseCouncil {

    @Id
    private UUID id;

    @Column(name = "round_id", nullable = false)
    private UUID roundId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(length = 120)
    private String room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CouncilStatus status;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ThesisDefenseCouncil() {
    }

    public ThesisDefenseCouncil(UUID roundId, UUID departmentId) {
        this.id = UUID.randomUUID();
        this.roundId = roundId;
        this.departmentId = departmentId;
        this.status = CouncilStatus.DRAFT;
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

    public void schedule(Instant nextScheduledAt, String nextRoom) {
        if (status != CouncilStatus.DRAFT) {
            throw new IllegalStateException("Only draft councils can be scheduled");
        }
        scheduledAt = nextScheduledAt;
        room = nextRoom;
        status = CouncilStatus.SCHEDULED;
    }

    public void openScoring() {
        if (status != CouncilStatus.SCHEDULED) {
            throw new IllegalStateException("Only scheduled councils can open scoring");
        }
        status = CouncilStatus.SCORING_OPEN;
    }

    public void finalizeCouncil() {
        if (status != CouncilStatus.SCORING_OPEN) {
            throw new IllegalStateException("Only councils with open scoring can be finalized");
        }
        status = CouncilStatus.FINALIZED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoundId() {
        return roundId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public String getRoom() {
        return room;
    }

    public CouncilStatus getStatus() {
        return status;
    }
}
