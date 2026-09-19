package io.campuscore.restfulapi.engagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA Entity representing an Article Media Gallery item in the engagement domain.
 * Maps to engagement."ArticleMediaGallery".
 */
@Entity
@Table(name = "\"ArticleMediaGallery\"", schema = "engagement")
public class ArticleMediaGallery {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"announcementId\"", nullable = false, length = 120)
    private String announcementId;

    @Column(name = "\"mediaUrl\"", nullable = false, length = 500)
    private String mediaUrl;

    @Column(name = "\"thumbnailUrl\"", length = 500)
    private String thumbnailUrl;

    @Column(name = "\"captionVi\"", nullable = false, length = 500)
    private String captionVi;

    @Column(name = "\"captionEn\"", length = 500)
    private String captionEn;

    @Column(name = "\"altText\"", nullable = false, length = 255)
    private String altText;

    @Column(name = "\"mediaType\"", nullable = false, length = 30)
    private String mediaType = "IMAGE";

    @Column(name = "\"aspectRatio\"", nullable = false, length = 20)
    private String aspectRatio = "16:9";

    private Integer width;

    private Integer height;

    @Column(name = "\"fileSizeBytes\"")
    private Long fileSizeBytes;

    @Column(name = "\"displayOrder\"", nullable = false)
    private int displayOrder = 0;

    @Column(name = "\"isCover\"", nullable = false)
    private boolean isCover = false;

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    private Instant createdAt;

    public ArticleMediaGallery() {}

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

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getCaptionVi() {
        return captionVi;
    }

    public void setCaptionVi(String captionVi) {
        this.captionVi = captionVi;
    }

    public String getCaptionEn() {
        return captionEn;
    }

    public void setCaptionEn(String captionEn) {
        this.captionEn = captionEn;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(String aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isCover() {
        return isCover;
    }

    public void setCover(boolean cover) {
        isCover = cover;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
