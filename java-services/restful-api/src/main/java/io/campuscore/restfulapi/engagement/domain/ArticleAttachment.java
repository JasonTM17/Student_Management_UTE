package io.campuscore.restfulapi.engagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA Entity representing an Article Attachment in the engagement domain.
 * Maps to engagement."ArticleAttachment".
 */
@Entity
@Table(name = "\"ArticleAttachment\"", schema = "engagement")
public class ArticleAttachment {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"announcementId\"", nullable = false, length = 120)
    private String announcementId;

    @Column(name = "\"fileName\"", nullable = false, length = 255)
    private String fileName;

    @Column(name = "\"fileUrl\"", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "\"fileSizeBytes\"", nullable = false)
    private long fileSizeBytes;

    @Column(name = "\"mimeType\"", nullable = false, length = 120)
    private String mimeType;

    @Column(name = "\"checksumSha256\"", length = 64)
    private String checksumSha256;

    @Column(name = "\"downloadCount\"", nullable = false)
    private int downloadCount = 0;

    @Column(name = "\"isPublic\"", nullable = false)
    private boolean isPublic = true;

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    private Instant createdAt;

    public ArticleAttachment() {}

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAnnouncementId() {
        return announcementId;
    }

    public void setAnnouncementId(String announcementId) {
        this.announcementId = announcementId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public int getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(int downloadCount) {
        this.downloadCount = downloadCount;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
