package io.campuscore.restfulapi.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to campuscore_audit.AdminAudit.
 */
@Entity
@Table(name = "\"AdminAudit\"", schema = "campuscore_audit")
public class AdminAudit {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"actorId\"", length = 120)
    private String actorId;

    @Column(name = "\"actorLabel\"", length = 240)
    private String actorLabel;

    @Column(nullable = false, length = 48)
    private String action;

    @Column(name = "\"entityType\"", nullable = false, length = 48)
    private String entityType;

    @Column(name = "\"entityId\"", length = 120)
    private String entityId;

    @Column(length = 500)
    private String summary;

    @Column(name = "\"beforeState\"", columnDefinition = "text")
    private String beforeState;

    @Column(name = "\"afterState\"", columnDefinition = "text")
    private String afterState;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AdminAudit() {
    }

    public String getId() {
        return id;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getSummary() {
        return summary;
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
        if (!(o instanceof AdminAudit other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
