package io.campuscore.restfulapi.academic.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "RegistrationSlip", schema = "academic")
public class RegistrationSlipEntity {
    @Id private String id;
    @Column(name = "studentId", nullable = false) private String studentId;
    @Column(name = "roundId", nullable = false) private String roundId;
    @Column(name = "contentHash", nullable = false, columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String contentHash;
    @Column(name = "snapshotPayload", columnDefinition = "text") private String snapshotPayload;
    @Column(name = "generatedAt") private Instant generatedAt;
    protected RegistrationSlipEntity() { }
    public static RegistrationSlipEntity snapshot(String id, String studentId, String roundId,
                                                  String contentHash, Instant generatedAt) {
        return snapshot(id, studentId, roundId, contentHash, null, generatedAt);
    }
    public static RegistrationSlipEntity snapshot(String id, String studentId, String roundId,
                                                  String contentHash, String snapshotPayload, Instant generatedAt) {
        RegistrationSlipEntity entity = new RegistrationSlipEntity();
        entity.id = required(id, "id");
        entity.studentId = required(studentId, "studentId");
        entity.roundId = required(roundId, "roundId");
        entity.contentHash = required(contentHash, "contentHash");
        if (!contentHash.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("contentHash must be SHA-256");
        entity.generatedAt = generatedAt;
        entity.snapshotPayload = snapshotPayload;
        if (snapshotPayload != null) entity.verifyAndSynchronizePayload(snapshotPayload, contentHash);
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
    public String getSnapshotPayload() { return snapshotPayload; }
    public void storePayload(String payload, String expectedHash) {
        verifyAndSynchronizePayload(payload, expectedHash);
    }
    private void verifyAndSynchronizePayload(String payload, String expectedHash) {
        String normalizedPayload = required(payload, "snapshotPayload");
        String normalizedHash = required(expectedHash, "expectedHash");
        if (!normalizedHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("expectedHash must be SHA-256");
        }
        byte[] pdf;
        try {
            pdf = Base64.getDecoder().decode(normalizedPayload);
        } catch (IllegalArgumentException malformedPayload) {
            throw new IllegalStateException("REGISTRATION_SLIP_PAYLOAD_INVALID", malformedPayload);
        }
        String actualHash = sha256(pdf);
        if (!actualHash.equalsIgnoreCase(normalizedHash)) {
            throw new IllegalStateException("REGISTRATION_SLIP_HASH_MISMATCH");
        }
        if (this.snapshotPayload != null && !this.snapshotPayload.equals(normalizedPayload)) {
            throw new IllegalStateException("REGISTRATION_SLIP_SNAPSHOT_IMMUTABLE");
        }
        this.snapshotPayload = normalizedPayload;
        // V17 snapshots stored the canonical-text hash. Synchronize upgraded
        // rows to the verifiable hash of the immutable PDF bytes.
        this.contentHash = actualHash;
    }
    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
