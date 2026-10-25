package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to academic.GradeItem.
 */
@Entity
@Table(name = "\"GradeItem\"", schema = "academic")
public class GradeItem {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

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

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected GradeItem() {
    }

    public String getId() {
        return id;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GradeItem other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
