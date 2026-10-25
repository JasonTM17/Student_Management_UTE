package io.campuscore.restfulapi.engagement.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for {@link AnnouncementTagMap}.
 */
public class AnnouncementTagMapId implements Serializable {

    private String announcementId;
    private String tagId;

    public AnnouncementTagMapId() {
    }

    public AnnouncementTagMapId(String announcementId, String tagId) {
        this.announcementId = announcementId;
        this.tagId = tagId;
    }

    public String getAnnouncementId() {
        return announcementId;
    }

    public String getTagId() {
        return tagId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnnouncementTagMapId other)) {
            return false;
        }
        return Objects.equals(announcementId, other.announcementId) && Objects.equals(tagId, other.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(announcementId, tagId);
    }
}
