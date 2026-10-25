package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to academic.Student.
 */
@Entity
@Table(name = "\"Student\"", schema = "academic")
public class Student {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"userId\"", nullable = false, length = 120)
    private String userId;

    @Column(name = "\"studentId\"", nullable = false, length = 120)
    private String studentId;

    @Column(name = "\"curriculumId\"", nullable = false, length = 120)
    private String curriculumId;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false, length = 40, insertable = false, updatable = false)
    private String status;

    @Column(name = "\"admissionDate\"", nullable = false)
    private Instant admissionDate;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"updatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Student() {
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getCurriculumId() {
        return curriculumId;
    }

    public int getYear() {
        return year;
    }

    public String getStatus() {
        return status;
    }

    public Instant getAdmissionDate() {
        return admissionDate;
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
        if (!(o instanceof Student other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
