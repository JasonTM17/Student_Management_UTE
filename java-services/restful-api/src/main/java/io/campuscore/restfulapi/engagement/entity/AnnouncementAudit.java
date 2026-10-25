package io.campuscore.restfulapi.engagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to engagement.AnnouncementAudit.
 */
@Entity
@Table(name = "\"AnnouncementAudit\"", schema = "engagement")
public class AnnouncementAudit {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"announcementId\"", nullable = false, length = 120)
    private String announcementId;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(name = "\"actorId\"", nullable = false, length = 120)
    private String actorId;

    @Column(name = "\"actorLabel\"", length = 240)
    private String actorLabel;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false)
    private int version;

    @Column(name = "\"beforeState\"", columnDefinition = "text")
    private String beforeState;

    @Column(name = "\"afterState\"", columnDefinition = "text")
    private String afterState;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AnnouncementAudit() {
    }

    public String getId() {
        return id;
    }

    public String getAnnouncementId() {
        return announcementId;
    }

    public String getAction() {
        return action;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public String getReason() {
        return reason;
    }

    public int getVersion() {
        return version;
    }

    public String getBeforeState() {
        return beforeState;
    }

    public String getAfterState() {
        return afterState;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnnouncementAudit other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
