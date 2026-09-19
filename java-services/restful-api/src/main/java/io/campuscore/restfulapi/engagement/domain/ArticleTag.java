package io.campuscore.restfulapi.engagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA Entity representing an Article Tag in the engagement domain.
 * Maps to engagement."ArticleTag".
 */
@Entity
@Table(name = "\"ArticleTag\"", schema = "engagement")
public class ArticleTag {

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @Column(nullable = false, length = 80, unique = true)
    private String slug;

    @Column(name = "\"nameVi\"", nullable = false, length = 80)
    private String nameVi;

    @Column(name = "\"nameEn\"", nullable = false, length = 80)
    private String nameEn;

    @Column(name = "\"usageCount\"", nullable = false)
    private int usageCount;

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    private Instant createdAt;

    protected ArticleTag() {
    }

    public ArticleTag(String id, String slug, String nameVi, String nameEn, int usageCount) {
        this.id = id;
        this.slug = slug;
        this.nameVi = nameVi;
        this.nameEn = nameEn;
        this.usageCount = usageCount;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onPersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getSlug() { return slug; }
    public String getNameVi() { return nameVi; }
    public String getNameEn() { return nameEn; }
    public int getUsageCount() { return usageCount; }
    public Instant getCreatedAt() { return createdAt; }

    public void incrementUsage() { this.usageCount++; }
}
