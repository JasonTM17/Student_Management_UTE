package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * JPA entity mapping to academic.conduct_activity.
 */
@Entity
@Table(name = "conduct_activity", schema = "academic")
public class ConductActivity {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "student_id", nullable = false, length = 120)
    private String studentId;

    @Column(name = "semester_id", nullable = false, length = 120)
    private String semesterId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 120)
    private String category;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal points;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(nullable = false, length = 180)
    private String organizer;

    @Column(name = "certificate_url", length = 255)
    private String certificateUrl;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected ConductActivity() {
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

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getPoints() {
        return points;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public String getOrganizer() {
        return organizer;
    }

    public String getCertificateUrl() {
        return certificateUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConductActivity other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
