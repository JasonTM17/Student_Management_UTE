package io.campuscore.engagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "announcement", schema = "engagement")
public class Announcement {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 20)
    private String priority;

    @Column(name = "target_roles", nullable = false, length = 50)
    private String targetRoles;

    @Column(name = "target_years", nullable = false, columnDefinition = "integer[]")
    private int[] targetYears;

    @Column(name = "is_global", nullable = false)
    private boolean global;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "publish_at")
    private Instant publishAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "semester_id")
    private UUID semesterId;

    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "lecturer_id")
    private UUID lecturerId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Announcement() {
    }

    public Announcement(String title, String description, String priority, String targetRoles, int[] targetYears) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.description = description;
        this.priority = priority == null ? "NORMAL" : priority;
        this.targetRoles = targetRoles == null ? "{}" : targetRoles;
        this.targetYears = targetYears == null ? new int[0] : targetYears;
        this.global = false;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getTargetRoles() {
        return targetRoles;
    }

    public void setTargetRoles(String targetRoles) {
        this.targetRoles = targetRoles;
    }

    public int[] getTargetYears() {
        return targetYears;
    }

    public void setTargetYears(int[] targetYears) {
        this.targetYears = targetYears;
    }

    public boolean isGlobal() {
        return global;
    }

    public void setGlobal(boolean global) {
        this.global = global;
    }

    public Instant getPublishAt() {
        return publishAt;
    }

    public void setPublishAt(Instant publishAt) {
        this.publishAt = publishAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public UUID getSemesterId() {
        return semesterId;
    }

    public void setSemesterId(UUID semesterId) {
        this.semesterId = semesterId;
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public void setSectionId(UUID sectionId) {
        this.sectionId = sectionId;
    }

    public UUID getLecturerId() {
        return lecturerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
