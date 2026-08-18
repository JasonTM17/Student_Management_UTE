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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "thesis_result", schema = "academic")
public class ThesisResult {

    @Id
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "total_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal totalScore;

    @Column(nullable = false, length = 16)
    private String grade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ResultStatus status;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ThesisResult() {
    }

    public ThesisResult(UUID groupId, BigDecimal totalScore, String grade) {
        this.id = UUID.randomUUID();
        this.groupId = groupId;
        this.totalScore = totalScore;
        this.grade = grade;
        this.status = ResultStatus.DRAFT;
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

    public void publish(UUID actorId) {
        if (status == ResultStatus.PUBLISHED) {
            throw new IllegalStateException("Result is already published");
        }
        status = ResultStatus.PUBLISHED;
        publishedBy = actorId;
        publishedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public String getGrade() {
        return grade;
    }

    public ResultStatus getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
