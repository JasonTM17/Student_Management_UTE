package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to academic.AcademicYear.
 */
@Entity
@Table(name = "\"AcademicYear\"", schema = "academic")
public class AcademicYear {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(nullable = false)
    private int year;

    @Column(name = "\"startDate\"", nullable = false)
    private Instant startDate;

    @Column(name = "\"endDate\"", nullable = false)
    private Instant endDate;

    @Column(name = "\"isCurrent\"", nullable = false, insertable = false, updatable = false)
    private boolean isCurrent;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"updatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected AcademicYear() {
    }

    public String getId() {
        return id;
    }

    public int getYear() {
        return year;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public boolean isCurrent() {
        return isCurrent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AcademicYear other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
