package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to academic.GradeItemLegacyArchive.
 */
@Entity
@Table(name = "\"GradeItemLegacyArchive\"", schema = "academic")
public class GradeItemLegacyArchive {

    @Id
    @Column(name = "\"sourceGradeItemId\"", nullable = false, length = 120)
    private String sourceGradeItemId;

    @Column(name = "\"sectionId\"", nullable = false, length = 120)
    private String sectionId;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(nullable = false, length = 60)
    private String type;

    @Column(name = "\"maxScore\"", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(name = "\"gradedAt\"")
    private Instant gradedAt;

    @Column(name = "\"createdAt\"")
    private Instant createdAt;

    @Column(name = "\"archivedByMigration\"", nullable = false, length = 40, insertable = false, updatable = false)
    private String archivedByMigration;

    @Column(name = "\"archivedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant archivedAt;

    protected GradeItemLegacyArchive() {
    }

    public String getSourceGradeItemId() {
        return sourceGradeItemId;
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public Instant getGradedAt() {
        return gradedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getArchivedByMigration() {
        return archivedByMigration;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GradeItemLegacyArchive other)) {
            return false;
        }
        return Objects.equals(sourceGradeItemId, other.sourceGradeItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(sourceGradeItemId);
    }
}
