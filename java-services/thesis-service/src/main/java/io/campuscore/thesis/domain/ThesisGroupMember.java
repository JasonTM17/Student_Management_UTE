package io.campuscore.thesis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "thesis_group_member", schema = "thesis")
public class ThesisGroupMember {

    @Id
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "round_id", nullable = false)
    private UUID roundId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "member_order", nullable = false)
    private int memberOrder;

    @Column(name = "is_leader", nullable = false)
    private boolean leader;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ThesisGroupMember() {
    }

    public ThesisGroupMember(UUID groupId, UUID roundId, UUID studentId, int memberOrder, boolean leader) {
        this.id = UUID.randomUUID();
        this.groupId = groupId;
        this.roundId = roundId;
        this.studentId = studentId;
        this.memberOrder = memberOrder;
        this.leader = leader;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getRoundId() {
        return roundId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public int getMemberOrder() {
        return memberOrder;
    }

    public boolean isLeader() {
        return leader;
    }
}
