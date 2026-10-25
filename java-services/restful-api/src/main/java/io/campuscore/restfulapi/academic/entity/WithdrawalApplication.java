package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to academic.WithdrawalApplication.
 */
@Entity
@Table(name = "\"WithdrawalApplication\"", schema = "academic")
public class WithdrawalApplication {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"studentId\"", nullable = false, length = 120)
    private String studentId;

    @Column(name = "\"enrollmentId\"", nullable = false, length = 120)
    private String enrollmentId;

    @Column(name = "\"semesterId\"", nullable = false, length = 120)
    private String semesterId;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false, length = 24, insertable = false, updatable = false)
    private String status;

    @Column(name = "\"reviewedBy\"", length = 120)
    private String reviewedBy;

    @Column(name = "\"reviewedAt\"")
    private Instant reviewedAt;

    @Column(name = "\"reviewerNote\"", length = 1000)
    private String reviewerNote;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"updatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private int version;

    protected WithdrawalApplication() {
    }

    public String getId() {
        return id;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getEnrollmentId() {
        return enrollmentId;
    }

    public String getSemesterId() {
        return semesterId;
    }

    public String getReason() {
        return reason;
    }

    public String getStatus() {
        return status;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewerNote() {
        return reviewerNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WithdrawalApplication other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
