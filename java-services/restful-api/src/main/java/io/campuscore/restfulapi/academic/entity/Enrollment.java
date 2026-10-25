package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to academic.Enrollment.
 */
@Entity
@Table(name = "\"Enrollment\"", schema = "academic")
public class Enrollment {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"studentId\"", nullable = false, length = 120)
    private String studentId;

    @Column(name = "\"sectionId\"", nullable = false, length = 120)
    private String sectionId;

    @Column(name = "\"semesterId\"", nullable = false, length = 120)
    private String semesterId;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "\"enrolledAt\"", nullable = false)
    private Instant enrolledAt;

    @Column(name = "\"droppedAt\"")
    private Instant droppedAt;

    @Column(name = "\"gradeStatus\"", nullable = false, length = 40)
    private String gradeStatus;

    @Column(name = "\"finalGrade\"", precision = 5, scale = 2)
    private BigDecimal finalGrade;

    @Column(name = "\"letterGrade\"", length = 16)
    private String letterGrade;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"updatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "\"gradePublishedAt\"")
    private Instant gradePublishedAt;

    @Column(name = "\"registrationSource\"", length = 40)
    private String registrationSource;

    @Column(name = "\"courseId\"", nullable = false, length = 120)
    private String courseId;

    @Column(name = "\"roundId\"", length = 120)
    private String roundId;

    @Column(name = "\"creditsSnapshot\"", nullable = false)
    private int creditsSnapshot;

    @Column(nullable = false, insertable = false, updatable = false)
    private int version;

    protected Enrollment() {
    }

    public String getId() {
        return id;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getSemesterId() {
        return semesterId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public Instant getDroppedAt() {
        return droppedAt;
    }

    public String getGradeStatus() {
        return gradeStatus;
    }

    public BigDecimal getFinalGrade() {
        return finalGrade;
    }

    public String getLetterGrade() {
        return letterGrade;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getGradePublishedAt() {
        return gradePublishedAt;
    }

    public String getRegistrationSource() {
        return registrationSource;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getRoundId() {
        return roundId;
    }

    public int getCreditsSnapshot() {
        return creditsSnapshot;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Enrollment other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
