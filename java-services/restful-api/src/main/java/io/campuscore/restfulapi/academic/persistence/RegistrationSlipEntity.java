package io.campuscore.restfulapi.academic.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "RegistrationSlip", schema = "academic")
public class RegistrationSlipEntity {
    @Id private String id;
    @Column(name = "studentId", nullable = false) private String studentId;
    @Column(name = "roundId", nullable = false) private String roundId;
    @Column(name = "contentHash", nullable = false, length = 64) private String contentHash;
    @Column(name = "generatedAt") private Instant generatedAt;
    protected RegistrationSlipEntity() { }
    public static RegistrationSlipEntity snapshot(String id, String studentId, String roundId,
                                                  String contentHash, Instant generatedAt) {
        RegistrationSlipEntity entity = new RegistrationSlipEntity();
        entity.id = required(id, "id");
        entity.studentId = required(studentId, "studentId");
        entity.roundId = required(roundId, "roundId");
        entity.contentHash = required(contentHash, "contentHash");
        if (!contentHash.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("contentHash must be SHA-256");
        entity.generatedAt = generatedAt;
        return entity;
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
    public String getId() { return id; }
    public String getStudentId() { return studentId; }
    public String getRoundId() { return roundId; }
    public String getContentHash() { return contentHash; }
    public Instant getGeneratedAt() { return generatedAt; }
}
