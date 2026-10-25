package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity mapping to academic.EnrollmentEvent.
 */
@Entity
@Table(name = "\"EnrollmentEvent\"", schema = "academic")
public class EnrollmentEvent {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"enrollmentId\"", nullable = false, length = 120)
    private String enrollmentId;

    @Column(name = "\"studentId\"", nullable = false, length = 120)
    private String studentId;

    @Column(name = "\"sectionId\"", nullable = false, length = 120)
    private String sectionId;

    @Column(nullable = false, length = 16)
    private String action;

    @Column(name = "\"actorId\"", nullable = false, length = 120)
    private String actorId;

    @Column(name = "\"requestHash\"", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String requestHash;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected EnrollmentEvent() {
    }

    public String getId() {
        return id;
    }

    public String getEnrollmentId() {
        return enrollmentId;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getAction() {
        return action;
    }

    public String getActorId() {
        return actorId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EnrollmentEvent other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
