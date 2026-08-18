package io.campuscore.thesis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "thesis_review", schema = "thesis")
public class ThesisReview {

    @Id
    private UUID id;

    @Column(name = "council_id", nullable = false)
    private UUID councilId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(length = 2000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReviewStatus status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected ThesisReview() {
    }

    public ThesisReview(UUID councilId, UUID groupId, UUID reviewerId, BigDecimal score, String comment) {
        this.id = UUID.randomUUID();
        this.councilId = councilId;
        this.groupId = groupId;
        this.reviewerId = reviewerId;
        this.score = score;
        this.comment = comment;
        this.status = ReviewStatus.SUBMITTED;
    }

    @PrePersist
    void onCreate() {
        submittedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCouncilId() {
        return councilId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public BigDecimal getScore() {
        return score;
    }

    public ReviewStatus getStatus() {
        return status;
    }
}
