package io.campuscore.thesis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "thesis_council_member", schema = "academic")
public class ThesisCouncilMember {

    @Id
    private UUID id;

    @Column(name = "council_id", nullable = false)
    private UUID councilId;

    @Column(name = "lecturer_id", nullable = false)
    private UUID lecturerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 32)
    private CouncilMemberRole memberRole;

    @Column(name = "member_order", nullable = false)
    private int memberOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ThesisCouncilMember() {
    }

    public ThesisCouncilMember(UUID councilId, UUID lecturerId, CouncilMemberRole memberRole, int memberOrder) {
        this.id = UUID.randomUUID();
        this.councilId = councilId;
        this.lecturerId = lecturerId;
        this.memberRole = memberRole;
        this.memberOrder = memberOrder;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCouncilId() {
        return councilId;
    }

    public UUID getLecturerId() {
        return lecturerId;
    }

    public CouncilMemberRole getMemberRole() {
        return memberRole;
    }

    public int getMemberOrder() {
        return memberOrder;
    }
}
