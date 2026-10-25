package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to academic.Section.
 */
@Entity
@Table(name = "\"Section\"", schema = "academic")
public class Section {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"sectionNumber\"", nullable = false, length = 80)
    private String sectionNumber;

    @Column(name = "\"courseId\"", nullable = false, length = 120)
    private String courseId;

    @Column(name = "\"semesterId\"", nullable = false, length = 120)
    private String semesterId;

    @Column(name = "\"lecturerId\"", length = 120)
    private String lecturerId;

    @Column(name = "\"classroomId\"", length = 120)
    private String classroomId;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "\"enrolledCount\"", nullable = false, insertable = false, updatable = false)
    private int enrolledCount;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"updatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private int version;

    protected Section() {
    }

    public String getId() {
        return id;
    }

    public String getSectionNumber() {
        return sectionNumber;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getSemesterId() {
        return semesterId;
    }

    public String getLecturerId() {
        return lecturerId;
    }

    public String getClassroomId() {
        return classroomId;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getEnrolledCount() {
        return enrolledCount;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Section other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
