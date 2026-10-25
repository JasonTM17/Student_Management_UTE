package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * JPA entity mapping to academic.StudentGrade.
 */
@Entity
@Table(name = "\"StudentGrade\"", schema = "academic")
public class StudentGrade {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"enrollmentId\"", nullable = false, length = 120)
    private String enrollmentId;

    @Column(name = "\"gradeItemId\"", nullable = false, length = 120)
    private String gradeItemId;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    protected StudentGrade() {
    }

    public String getId() {
        return id;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StudentGrade other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
