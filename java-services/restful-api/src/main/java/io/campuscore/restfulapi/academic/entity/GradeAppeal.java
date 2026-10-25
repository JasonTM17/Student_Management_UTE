package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to academic.GradeAppeal.
 */
@Entity
@Table(name = "\"GradeAppeal\"", schema = "academic")
public class GradeAppeal {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"enrollmentId\"", nullable = false, length = 120)
    private String enrollmentId;

    @Column(nullable = false, length = 24, insertable = false, updatable = false)
    private String status;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "\"resolutionReason\"", length = 1000)
    private String resolutionReason;

    @Column(name = "\"reviewedBy\"", length = 120)
    private String reviewedBy;

    @Column(name = "\"reviewedAt\"")
    private Instant reviewedAt;

    @Column(name = "\"beforeFinalGrade\"", precision = 5, scale = 2)
    private BigDecimal beforeFinalGrade;

    @Column(name = "\"beforeLetterGrade\"", length = 16)
    private String beforeLetterGrade;

    @Column(name = "\"afterFinalGrade\"", precision = 5, scale = 2)
    private BigDecimal afterFinalGrade;

    @Column(name = "\"afterLetterGrade\"", length = 16)
    private String afterLetterGrade;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"updatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private int version;

    protected GradeAppeal() {
    }

    public String getId() {
        return id;
    }

    public String getEnrollmentId() {
        return enrollmentId;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public String getResolutionReason() {
        return resolutionReason;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public BigDecimal getBeforeFinalGrade() {
        return beforeFinalGrade;
    }

    public String getBeforeLetterGrade() {
        return beforeLetterGrade;
    }

    public BigDecimal getAfterFinalGrade() {
        return afterFinalGrade;
    }

    public String getAfterLetterGrade() {
        return afterLetterGrade;
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
        if (!(o instanceof GradeAppeal other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
