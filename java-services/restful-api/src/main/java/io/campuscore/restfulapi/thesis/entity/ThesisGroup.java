package io.campuscore.restfulapi.thesis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping to thesis.thesis_group.
 */
@Entity
@Table(name = "thesis_group", schema = "thesis")
public class ThesisGroup {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "round_id", nullable = false)
    private UUID roundId;

    @Column(name = "leader_student_id", nullable = false, length = 120)
    private String leaderStudentId;

    @Column(name = "topic_id")
    private UUID topicId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "approval_status", nullable = false, length = 32)
    private String approvalStatus;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(nullable = false, insertable = false, updatable = false)
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "legacy_group_size_exception", nullable = false, insertable = false, updatable = false)
    private boolean legacyGroupSizeException;

    @Column(name = "size_exception_reason", length = 500)
    private String sizeExceptionReason;

    @Column(name = "size_exception_by", length = 120)
    private String sizeExceptionBy;

    @Column(name = "size_exception_at")
    private Instant sizeExceptionAt;

    protected ThesisGroup() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoundId() {
        return roundId;
    }

    public String getLeaderStudentId() {
        return leaderStudentId;
    }

    public UUID getTopicId() {
        return topicId;
    }

    public String getStatus() {
        return status;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isLegacyGroupSizeException() {
        return legacyGroupSizeException;
    }

    public String getSizeExceptionReason() {
        return sizeExceptionReason;
    }

    public String getSizeExceptionBy() {
        return sizeExceptionBy;
    }

    public Instant getSizeExceptionAt() {
        return sizeExceptionAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ThesisGroup other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
