package io.campuscore.restfulapi.academic.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "EnrollmentAudit", schema = "academic")
public class EnrollmentAuditEntity {
    @Id private String id;
    @Column(name = "operationId") private String operationId;
    @Column(name = "studentId", nullable = false) private String studentId;
    @Column(name = "sectionId", nullable = false) private String sectionId;
    @Column(name = "action", nullable = false, length = 20) private String action;
    @Column(name = "reasonCode") private String reasonCode;
    @Column(name = "createdAt") private Instant createdAt;
    protected EnrollmentAuditEntity() { }
    public static EnrollmentAuditEntity record(String id, String operationId, String studentId,
                                               String sectionId, String action, String reasonCode,
                                               Instant now) {
        EnrollmentAuditEntity entity = new EnrollmentAuditEntity();
        entity.id = required(id, "id");
        entity.operationId = operationId;
        entity.studentId = required(studentId, "studentId");
        entity.sectionId = required(sectionId, "sectionId");
        entity.action = required(action, "action");
        entity.reasonCode = reasonCode;
        entity.createdAt = now;
        return entity;
    }
    /** Alias kept for service code that treats audit entries as immutable values. */
    public static EnrollmentAuditEntity of(String id, String operationId, String studentId,
                                           String sectionId, String action, String reasonCode,
                                           Instant now) {
        return record(id, operationId, studentId, sectionId, action, reasonCode, now);
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
    public String getId() { return id; }
    public String getOperationId() { return operationId; }
    public String getStudentId() { return studentId; }
    public String getSectionId() { return sectionId; }
    public String getAction() { return action; }
    public String getReasonCode() { return reasonCode; }
    public Instant getCreatedAt() { return createdAt; }
}
