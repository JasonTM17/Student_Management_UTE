package io.campuscore.restfulapi.thesis.domain;

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
@Table(name = "thesis_topic", schema = "thesis")
public class ThesisTopic {

    @Id
    private UUID id;

    @Column(name = "round_id", nullable = false)
    private UUID roundId;

    @Column(name = "department_id", nullable = false)
    private String departmentId;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "max_groups", nullable = false)
    private int maxGroups;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TopicStatus status;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ThesisTopic() {
    }

    public ThesisTopic(
            UUID roundId,
            String departmentId,
            String title,
            String description,
            int maxGroups,
            String createdBy) {
        this.id = UUID.randomUUID();
        this.roundId = roundId;
        this.departmentId = departmentId;
        this.title = title;
        this.description = description;
        this.maxGroups = maxGroups;
        this.createdBy = createdBy;
        this.status = TopicStatus.DRAFT;
    }

    /** Compatibility constructor for persistence fixtures that use UUID placeholders. */
    public ThesisTopic(
            UUID roundId,
            UUID departmentId,
            String title,
            String description,
            int maxGroups,
            UUID createdBy) {
        this(roundId, departmentId == null ? null : departmentId.toString(), title, description, maxGroups,
                createdBy == null ? null : createdBy.toString());
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

    public UUID getId() {
        return id;
    }

    public UUID getRoundId() {
        return roundId;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getMaxGroups() {
        return maxGroups;
    }

    public TopicStatus getStatus() {
        return status;
    }

    public void publish() {
        if (status != TopicStatus.DRAFT) {
            throw new IllegalStateException("Only draft topics can be published");
        }
        status = TopicStatus.PUBLISHED;
    }

    public void update(String departmentId, String title, String description, int maxGroups) {
        this.departmentId = departmentId;
        this.title = title;
        this.description = description;
        this.maxGroups = maxGroups;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
