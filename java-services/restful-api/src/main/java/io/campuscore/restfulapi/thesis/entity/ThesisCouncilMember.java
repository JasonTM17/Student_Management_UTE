package io.campuscore.restfulapi.thesis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping to thesis.thesis_council_member.
 */
@Entity
@Table(name = "thesis_council_member", schema = "thesis")
public class ThesisCouncilMember {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "council_id", nullable = false)
    private UUID councilId;

    @Column(name = "lecturer_id", nullable = false, length = 120)
    private String lecturerId;

    @Column(name = "member_role", nullable = false, length = 20)
    private String memberRole;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ThesisCouncilMember() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getCouncilId() {
        return councilId;
    }

    public String getLecturerId() {
        return lecturerId;
    }

    public String getMemberRole() {
        return memberRole;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ThesisCouncilMember other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
