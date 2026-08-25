package io.campuscore.restfulapi.academic.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/** Mutable JPA aggregate used by the enrollment transaction boundary. */
@Entity
@Table(name = "Enrollment", schema = "academic")
public class EnrollmentEntity {
    @Id private String id;
    @Column(name = "studentId", nullable = false) private String studentId;
    @Column(name = "sectionId", nullable = false) private String sectionId;
    @Column(name = "semesterId", nullable = false) private String semesterId;
    @Column(name = "roundId", nullable = false) private String roundId;
    @Column(name = "status", nullable = false, length = 40) private String status;
    @Column(name = "enrolledAt", nullable = false) private Instant enrolledAt;
    @Column(name = "droppedAt") private Instant droppedAt;
    @Column(name = "gradeStatus", nullable = false, length = 40) private String gradeStatus;
    @Column(name = "finalGrade", precision = 5, scale = 2) private BigDecimal finalGrade;
    @Column(name = "letterGrade", length = 16) private String letterGrade;
    @Column(name = "createdAt", nullable = false) private Instant createdAt;
    @Column(name = "updatedAt", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version") private long version;

    protected EnrollmentEntity() { }

    public static EnrollmentEntity active(String id, String studentId, String sectionId,
                                           String semesterId, String roundId, Instant now) {
        return create(id, studentId, sectionId, semesterId, roundId, now, "ACTIVE", "PENDING");
    }

    public static EnrollmentEntity enrolled(String id, String studentId, String sectionId,
                                             String semesterId, String roundId, Instant now) {
        return create(id, studentId, sectionId, semesterId, roundId, now, "ENROLLED", "NOT_GRADED");
    }

    private static EnrollmentEntity create(String id, String studentId, String sectionId,
                                            String semesterId, String roundId, Instant now, String status,
                                            String gradeStatus) {
        EnrollmentEntity entity = new EnrollmentEntity();
        entity.id = require(id, "id");
        entity.studentId = require(studentId, "studentId");
        entity.sectionId = require(sectionId, "sectionId");
        entity.semesterId = require(semesterId, "semesterId");
        entity.roundId = require(roundId, "roundId");
        entity.status = status;
        entity.enrolledAt = now;
        entity.gradeStatus = gradeStatus;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void markDropped(Instant now) {
        this.status = "DROPPED";
        this.droppedAt = now;
        this.updatedAt = now;
    }

    public void updateGrade(BigDecimal finalGrade, String letterGrade, Instant now) {
        this.finalGrade = finalGrade;
        this.letterGrade = require(letterGrade, "letterGrade").trim().toUpperCase(java.util.Locale.ROOT);
        this.gradeStatus = "DRAFT";
        this.updatedAt = now;
    }

    public boolean publishGrade(Instant now) {
        if (finalGrade == null || letterGrade == null || letterGrade.isBlank()) return false;
        this.gradeStatus = "PUBLISHED";
        this.status = "COMPLETED";
        this.updatedAt = now;
        return true;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    public String getId() { return id; }
    public String getStudentId() { return studentId; }
    public String getSectionId() { return sectionId; }
    public String getSemesterId() { return semesterId; }
    public String getRoundId() { return roundId; }
    public String getStatus() { return status; }
    public Instant getEnrolledAt() { return enrolledAt; }
    public Instant getDroppedAt() { return droppedAt; }
    public String getGradeStatus() { return gradeStatus; }
    public BigDecimal getFinalGrade() { return finalGrade; }
    public String getLetterGrade() { return letterGrade; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
