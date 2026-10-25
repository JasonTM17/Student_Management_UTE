package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to academic.StudentGradeLegacyArchive.
 */
@Entity
@Table(name = "\"StudentGradeLegacyArchive\"", schema = "academic")
public class StudentGradeLegacyArchive {

    @Id
    @Column(name = "\"sourceStudentGradeId\"", nullable = false, length = 120)
    private String sourceStudentGradeId;

    @Column(name = "\"enrollmentId\"", nullable = false, length = 120)
    private String enrollmentId;

    @Column(name = "\"gradeItemId\"", nullable = false, length = 120)
    private String gradeItemId;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "\"archivedByMigration\"", nullable = false, length = 40, insertable = false, updatable = false)
    private String archivedByMigration;

    @Column(name = "\"archivedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant archivedAt;

    protected StudentGradeLegacyArchive() {
    }

    public String getSourceStudentGradeId() {
        return sourceStudentGradeId;
    }

    public String getEnrollmentId() {
        return enrollmentId;
    }

    public String getGradeItemId() {
        return gradeItemId;
    }

    public BigDecimal getScore() {
        return score;
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
        if (!(o instanceof StudentGradeLegacyArchive other)) {
            return false;
        }
        return Objects.equals(sourceStudentGradeId, other.sourceStudentGradeId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(sourceStudentGradeId);
    }
}
