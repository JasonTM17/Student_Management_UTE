package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to academic.conduct_semester_score.
 */
@Entity
@Table(name = "conduct_semester_score", schema = "academic")
public class ConductSemesterScore {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "student_id", nullable = false, length = 120)
    private String studentId;

    @Column(name = "semester_id", nullable = false, length = 120)
    private String semesterId;

    @Column(name = "criteria1_score", nullable = false, precision = 5, scale = 2, insertable = false, updatable = false)
    private BigDecimal criteria1Score;

    @Column(name = "criteria2_score", nullable = false, precision = 5, scale = 2, insertable = false, updatable = false)
    private BigDecimal criteria2Score;

    @Column(name = "criteria3_score", nullable = false, precision = 5, scale = 2, insertable = false, updatable = false)
    private BigDecimal criteria3Score;

    @Column(name = "criteria4_score", nullable = false, precision = 5, scale = 2, insertable = false, updatable = false)
    private BigDecimal criteria4Score;

    @Column(name = "criteria5_score", nullable = false, precision = 5, scale = 2, insertable = false, updatable = false)
    private BigDecimal criteria5Score;

    @Column(name = "total_score", nullable = false, precision = 5, scale = 2, insertable = false, updatable = false)
    private BigDecimal totalScore;

    @Column(nullable = false, length = 32)
    private String classification;

    @Column(name = "classification_vi", nullable = false, length = 60)
    private String classificationVi;

    @Column(nullable = false, length = 32, insertable = false, updatable = false)
    private String status;

    @Column(name = "evaluator_name", length = 160)
    private String evaluatorName;

    @Column(name = "evaluated_at", insertable = false, updatable = false)
    private Instant evaluatedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected ConductSemesterScore() {
    }

    public String getId() {
        return id;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getSemesterId() {
        return semesterId;
    }

    public BigDecimal getCriteria1Score() {
        return criteria1Score;
    }

    public BigDecimal getCriteria2Score() {
        return criteria2Score;
    }

    public BigDecimal getCriteria3Score() {
        return criteria3Score;
    }

    public BigDecimal getCriteria4Score() {
        return criteria4Score;
    }

    public BigDecimal getCriteria5Score() {
        return criteria5Score;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public String getClassification() {
        return classification;
    }

    public String getClassificationVi() {
        return classificationVi;
    }

    public String getStatus() {
        return status;
    }

    public String getEvaluatorName() {
        return evaluatorName;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
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
        if (!(o instanceof ConductSemesterScore other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
