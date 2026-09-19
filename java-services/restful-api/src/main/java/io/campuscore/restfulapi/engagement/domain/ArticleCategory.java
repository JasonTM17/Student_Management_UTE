package io.campuscore.restfulapi.engagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA Entity representing an Article Category in the engagement domain.
 * Maps to engagement."ArticleCategory".
 */
@Entity
@Table(name = "\"ArticleCategory\"", schema = "engagement")
public class ArticleCategory {

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @Column(nullable = false, length = 32, unique = true)
    private String code;

    @Column(name = "\"nameVi\"", nullable = false, length = 120)
    private String nameVi;

    @Column(name = "\"nameEn\"", nullable = false, length = 120)
    private String nameEn;

    @Column(nullable = false, length = 80, unique = true)
    private String slug;

    @Column(length = 500)
    private String description;

    @Column(name = "\"colorTone\"", nullable = false, length = 32)
    private String colorTone;

    @Column(name = "\"iconType\"", nullable = false, length = 64)
    private String iconType;

    @Column(name = "\"displayOrder\"", nullable = false)
    private int displayOrder;

    @Column(name = "\"isActive\"", nullable = false)
    private boolean isActive;

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"updatedAt\"", nullable = false)
    private Instant updatedAt;

    protected ArticleCategory() {
    }

    public ArticleCategory(
            String id,
            String code,
            String nameVi,
            String nameEn,
            String slug,
            String description,
            String colorTone,
            String iconType,
            int displayOrder,
            boolean isActive) {
        this.id = id;
        this.code = code;
        this.nameVi = nameVi;
        this.nameEn = nameEn;
        this.slug = slug;
        this.description = description;
        this.colorTone = colorTone;
        this.iconType = iconType;
        this.displayOrder = displayOrder;
        this.isActive = isActive;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getNameVi() { return nameVi; }
    public String getNameEn() { return nameEn; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getColorTone() { return colorTone; }
    public String getIconType() { return iconType; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return isActive; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public void setActive(boolean active) { this.isActive = active; }
    public void setDescription(String description) { this.description = description; }
}
