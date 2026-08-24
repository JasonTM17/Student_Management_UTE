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
    public String getId() { return id; }
    public String getStudentId() { return studentId; }
    public String getRoundId() { return roundId; }
    public String getContentHash() { return contentHash; }
    public Instant getGeneratedAt() { return generatedAt; }
}
