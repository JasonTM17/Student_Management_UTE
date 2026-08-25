package io.campuscore.restfulapi.academic.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "EnrollmentOperation", schema = "academic")
public class EnrollmentOperationEntity {
    @Id private String id;
    @Column(name = "studentId", nullable = false) private String studentId;
    @Column(name = "idempotencyKey", nullable = false) private String idempotencyKey;
    @Column(name = "canonicalRequestHash", nullable = false, columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String canonicalRequestHash;
    @Column(name = "operationType", nullable = false, length = 20) private String operationType;
    @Column(name = "state", nullable = false, length = 40) private String state;
    @Column(name = "responseStatus") private Integer responseStatus;
    @Column(name = "responseBody", columnDefinition = "text") private String responseBody;
    @Column(name = "createdAt") private Instant createdAt;
    @Column(name = "updatedAt") private Instant updatedAt;
    @Column(name = "completedAt") private Instant completedAt;
    @Version @Column(name = "version") private long version;
    protected EnrollmentOperationEntity() { }
    public static EnrollmentOperationEntity processing(String id, String studentId, String idempotencyKey,
                                                       String canonicalRequestHash, String operationType,
                                                       Instant now) {
        EnrollmentOperationEntity entity = new EnrollmentOperationEntity();
        entity.id = required(id, "id");
        entity.studentId = required(studentId, "studentId");
        entity.idempotencyKey = required(idempotencyKey, "idempotencyKey");
        entity.canonicalRequestHash = required(canonicalRequestHash, "canonicalRequestHash");
        if (!canonicalRequestHash.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("canonicalRequestHash must be SHA-256");
        entity.operationType = required(operationType, "operationType");
        entity.state = "PROCESSING";
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }
    public void complete(int responseStatus, String responseBody, Instant now) {
        if (!"PROCESSING".equals(state)) throw new IllegalStateException("OPERATION_NOT_PROCESSING");
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.state = "COMPLETED";
        this.completedAt = now;
        this.updatedAt = now;
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
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
