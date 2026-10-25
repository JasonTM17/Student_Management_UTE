package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to academic.Curriculum.
 */
@Entity
@Table(name = "\"Curriculum\"", schema = "academic")
public class Curriculum {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "\"nameEn\"", length = 180)
    private String nameEn;

    @Column(name = "\"nameVi\"", length = 180)
    private String nameVi;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(name = "\"departmentId\"", nullable = false, length = 120)
    private String departmentId;

    @Column(name = "\"academicYearId\"", nullable = false, length = 120)
    private String academicYearId;

    @Column(name = "\"semesterId\"", length = 120)
    private String semesterId;

    @Column(name = "\"totalCredits\"", nullable = false, insertable = false, updatable = false)
    private int totalCredits;

    @Column(length = 1000)
    private String description;

    @Column(name = "\"descriptionEn\"", length = 1000)
    private String descriptionEn;

    @Column(name = "\"descriptionVi\"", length = 1000)
    private String descriptionVi;

    @Column(name = "\"isActive\"", nullable = false, insertable = false, updatable = false)
    private boolean isActive;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"updatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Curriculum() {
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getNameVi() {
        return nameVi;
    }

    public String getCode() {
        return code;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public String getAcademicYearId() {
        return academicYearId;
    }

    public String getSemesterId() {
        return semesterId;
    }

    public int getTotalCredits() {
        return totalCredits;
    }

    public String getDescription() {
        return description;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public String getDescriptionVi() {
        return descriptionVi;
    }

    public boolean isActive() {
        return isActive;
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
        if (!(o instanceof Curriculum other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
