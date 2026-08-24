package io.campuscore.restfulapi.academic.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "EnrollmentOperation", schema = "academic")
public class EnrollmentOperationEntity {
    @Id private String id;
    @Column(name = "studentId", nullable = false) private String studentId;
    @Column(name = "idempotencyKey", nullable = false) private String idempotencyKey;
    @Column(name = "canonicalRequestHash", nullable = false, length = 64) private String canonicalRequestHash;
    @Column(name = "operationType", nullable = false, length = 20) private String operationType;
    @Column(name = "state", nullable = false, length = 40) private String state;
    @Column(name = "responseStatus") private Integer responseStatus;
    @Column(name = "responseBody", columnDefinition = "text") private String responseBody;
    @Column(name = "createdAt") private Instant createdAt;
    @Column(name = "updatedAt") private Instant updatedAt;
    @Column(name = "completedAt") private Instant completedAt;
    @Version @Column(name = "version") private long version;
    protected EnrollmentOperationEntity() { }
    public String getId() { return id; }
    public String getStudentId() { return studentId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getCanonicalRequestHash() { return canonicalRequestHash; }
    public String getOperationType() { return operationType; }
    public String getState() { return state; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getVersion() { return version; }
}
