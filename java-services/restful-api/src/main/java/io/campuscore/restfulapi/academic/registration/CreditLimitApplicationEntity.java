package io.campuscore.restfulapi.academic.registration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Spring Data JPA ORM entity for CreditLimitApplication.
 * Maps to academic."CreditLimitApplication".
 */
@Entity
@Table(name = "\"CreditLimitApplication\"", schema = "academic")
public class CreditLimitApplicationEntity {

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @Column(name = "\"studentId\"", nullable = false, length = 64)
    private String studentId;

    @Column(name = "\"semesterId\"", nullable = false, length = 64)
    private String semesterId;

    @Column(name = "\"roundId\"", nullable = false, length = 64)
    private String roundId;

    @Column(name = "\"requestedLimit\"", nullable = false)
    private int requestedLimit;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "\"reviewedBy\"", length = 64)
    private String reviewedBy;

    @Column(name = "\"reviewedAt\"")
    private Instant reviewedAt;

    @Column(name = "\"reviewerNote\"", length = 1000)
    private String reviewerNote;

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"updatedAt\"", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected CreditLimitApplicationEntity() {
    }

    public CreditLimitApplicationEntity(
            String id,
            String studentId,
            String semesterId,
            String roundId,
            int requestedLimit,
            String reason,
            String status) {
        this.id = id;
        this.studentId = studentId;
        this.semesterId = semesterId;
        this.roundId = roundId;
        this.requestedLimit = requestedLimit;
        this.reason = reason;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 1;
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getStudentId() { return studentId; }
    public String getSemesterId() { return semesterId; }
    public String getRoundId() { return roundId; }
    public int getRequestedLimit() { return requestedLimit; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewerNote() { return reviewerNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }

    public void setStatus(String status) { this.status = status; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public void setReviewerNote(String reviewerNote) { this.reviewerNote = reviewerNote; }
}
