package io.campuscore.restfulapi.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to notifications.notification.
 */
@Entity
@Table(name = "notification", schema = "notifications")
public class Notification {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "user_id", nullable = false, length = 120)
    private String userId;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(nullable = false, length = 60)
    private String type;

    @Column(length = 500)
    private String link;

    @Column(name = "is_read", nullable = false, insertable = false, updatable = false)
    private boolean isRead;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "announcement_id", length = 120)
    private String announcementId;

    @Column(name = "source_key", length = 160)
    private String sourceKey;

    protected Notification() {
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getType() {
        return type;
    }

    public String getLink() {
        return link;
    }

    public boolean isRead() {
        return isRead;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getAnnouncementId() {
        return announcementId;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Notification other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
