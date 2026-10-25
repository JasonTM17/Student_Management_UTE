package io.campuscore.restfulapi.engagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to engagement.AnnouncementTagMap.
 */
@Entity
@IdClass(AnnouncementTagMapId.class)
@Table(name = "\"AnnouncementTagMap\"", schema = "engagement")
public class AnnouncementTagMap {

    @Id
    @Column(name = "\"announcementId\"", nullable = false, length = 120)
    private String announcementId;

    @Id
    @Column(name = "\"tagId\"", nullable = false, length = 60)
    private String tagId;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AnnouncementTagMap() {
    }

    public String getAnnouncementId() {
        return announcementId;
    }

    public String getTagId() {
        return tagId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnnouncementTagMap other)) {
            return false;
        }
        return Objects.equals(announcementId, other.announcementId) && Objects.equals(tagId, other.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(announcementId, tagId);
    }
}
