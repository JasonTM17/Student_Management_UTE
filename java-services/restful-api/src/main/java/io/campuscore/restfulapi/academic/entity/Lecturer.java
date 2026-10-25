package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to academic.Lecturer.
 */
@Entity
@Table(name = "\"Lecturer\"", schema = "academic")
public class Lecturer {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"userId\"", nullable = false, length = 120)
    private String userId;

    @Column(name = "\"departmentId\"", nullable = false, length = 120)
    private String departmentId;

    @Column(name = "\"employeeId\"", nullable = false, length = 120)
    private String employeeId;

    @Column(length = 160)
    private String title;

    @Column(length = 240)
    private String specialization;

    @Column(length = 120)
    private String office;

    @Column(length = 80)
    private String phone;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"updatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "\"isActive\"", nullable = false, insertable = false, updatable = false)
    private boolean isActive;

    protected Lecturer() {
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getTitle() {
        return title;
    }

    public String getSpecialization() {
        return specialization;
    }

    public String getOffice() {
        return office;
    }

    public String getPhone() {
        return phone;
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
        if (!(o instanceof Lecturer other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
