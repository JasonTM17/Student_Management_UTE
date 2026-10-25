package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to academic.Department.
 */
@Entity
@Table(name = "\"Department\"", schema = "academic")
public class Department {

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

    @Column(length = 1000)
    private String description;

    @Column(name = "\"descriptionEn\"", length = 1000)
    private String descriptionEn;

    @Column(name = "\"descriptionVi\"", length = 1000)
    private String descriptionVi;

    @Column(length = 160)
    private String chair;

    @Column(length = 80)
    private String phone;

    @Column(length = 320)
    private String email;

    @Column(length = 160)
    private String building;

    @Column(name = "\"facultyId\"", nullable = false, length = 120)
    private String facultyId;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"updatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "\"isActive\"", nullable = false, insertable = false, updatable = false)
    private boolean isActive;

    protected Department() {
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

    public String getDescription() {
        return description;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public String getDescriptionVi() {
        return descriptionVi;
    }

    public String getChair() {
        return chair;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getBuilding() {
        return building;
    }

    public String getFacultyId() {
        return facultyId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return isActive;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Department other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
