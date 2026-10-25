package io.campuscore.restfulapi.thesis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping to thesis.thesis_group_member.
 */
@Entity
@Table(name = "thesis_group_member", schema = "thesis")
public class ThesisGroupMember {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "round_id", nullable = false)
    private UUID roundId;

    @Column(name = "student_id", nullable = false, length = 120)
    private String studentId;

    @Column(name = "member_order", nullable = false)
    private int memberOrder;

    @Column(name = "is_leader", nullable = false, insertable = false, updatable = false)
    private boolean isLeader;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Column(length = 150)
    private String contact;

    @Column(name = "is_external", nullable = false, insertable = false, updatable = false)
    private boolean isExternal;

    @Column(name = "active_participation", nullable = false, insertable = false, updatable = false)
    private boolean activeParticipation;

    protected ThesisGroupMember() {
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

    public String getStudentId() {
        return studentId;
    }

    public int getMemberOrder() {
        return memberOrder;
    }

    public boolean isLeader() {
        return isLeader;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getContact() {
        return contact;
    }

    public boolean isExternal() {
        return isExternal;
    }

    public boolean isActiveParticipation() {
        return activeParticipation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ThesisGroupMember other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
