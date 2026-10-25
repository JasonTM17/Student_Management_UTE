package io.campuscore.restfulapi.engagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to engagement.ArticleReadingMetric.
 */
@Entity
@Table(name = "\"ArticleReadingMetric\"", schema = "engagement")
public class ArticleReadingMetric {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"announcementId\"", nullable = false, length = 120)
    private String announcementId;

    @Column(name = "\"readerUserId\"", length = 120)
    private String readerUserId;

    @Column(name = "\"ipAddressHash\"", nullable = false, length = 64)
    private String ipAddressHash;

    @Column(name = "\"userAgentHash\"", nullable = false, length = 64)
    private String userAgentHash;

    @Column(name = "\"dwellTimeSeconds\"", nullable = false, insertable = false, updatable = false)
    private int dwellTimeSeconds;

    @Column(name = "\"readPercentage\"", nullable = false, insertable = false, updatable = false)
    private int readPercentage;

    @Column(name = "\"firstReadAt\"", nullable = false, insertable = false, updatable = false)
    private Instant firstReadAt;

    @Column(name = "\"lastReadAt\"", nullable = false, insertable = false, updatable = false)
    private Instant lastReadAt;

    protected ArticleReadingMetric() {
    }

    public String getId() {
        return id;
    }

    public String getAnnouncementId() {
        return announcementId;
    }

    public String getReaderUserId() {
        return readerUserId;
    }

    public String getIpAddressHash() {
        return ipAddressHash;
    }

    public String getUserAgentHash() {
        return userAgentHash;
    }

    public int getDwellTimeSeconds() {
        return dwellTimeSeconds;
    }

    public int getReadPercentage() {
        return readPercentage;
    }

    public Instant getFirstReadAt() {
        return firstReadAt;
    }

    public Instant getLastReadAt() {
        return lastReadAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ArticleReadingMetric other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
