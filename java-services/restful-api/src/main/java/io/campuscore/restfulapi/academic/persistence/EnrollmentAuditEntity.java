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
    public String getId() { return id; }
    public String getOperationId() { return operationId; }
    public String getStudentId() { return studentId; }
    public String getSectionId() { return sectionId; }
    public String getAction() { return action; }
    public String getReasonCode() { return reasonCode; }
    public Instant getCreatedAt() { return createdAt; }
}
