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
@Table(name = "thesis_group", schema = "thesis")
public class ThesisGroup {

    @Id
    private UUID id;

    @Column(name = "round_id", nullable = false)
    private UUID roundId;

    @Column(name = "leader_student_id", nullable = false)
    private UUID leaderStudentId;

    @Column(name = "topic_id")
    private UUID topicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GroupStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 32)
    private ApprovalStatus approvalStatus;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ThesisGroup() {
    }

    public ThesisGroup(UUID roundId, UUID leaderStudentId) {
        this.id = UUID.randomUUID();
        this.roundId = roundId;
        this.leaderStudentId = leaderStudentId;
        this.status = GroupStatus.DRAFT;
        this.approvalStatus = ApprovalStatus.PENDING;
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

    public void assignTopic(UUID nextTopicId) {
        if (status == GroupStatus.CANCELLED || approvalStatus == ApprovalStatus.APPROVED) {
            throw new IllegalStateException("This group can no longer change its topic");
        }
        topicId = nextTopicId;
        status = GroupStatus.SUBMITTED;
    }

    public void approve(UUID actorId) {
        if (topicId == null || status == GroupStatus.CANCELLED) {
            throw new IllegalStateException("A submitted topic is required before approval");
        }
        approvalStatus = ApprovalStatus.APPROVED;
        status = GroupStatus.APPROVED;
        approvedBy = actorId;
        approvedAt = Instant.now();
        rejectionReason = null;
    }

    public void reject(String reason) {
        if (status == GroupStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled groups cannot be rejected");
        }
        approvalStatus = ApprovalStatus.REJECTED;
        status = GroupStatus.REJECTED;
        rejectionReason = reason;
        approvedBy = null;
        approvedAt = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoundId() {
        return roundId;
    }

    public UUID getLeaderStudentId() {
        return leaderStudentId;
    }

    public UUID getTopicId() {
        return topicId;
    }

    public GroupStatus getStatus() {
        return status;
    }

    public ApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}
