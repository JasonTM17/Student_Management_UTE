package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity mapping to academic.RegistrationSlip.
 */
@Entity
@Table(name = "\"RegistrationSlip\"", schema = "academic")
public class RegistrationSlip {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"studentId\"", nullable = false, length = 120)
    private String studentId;

    @Column(name = "\"semesterId\"", nullable = false, length = 120)
    private String semesterId;

    @Column(name = "\"roundId\"", nullable = false, length = 120)
    private String roundId;

    @Column(nullable = false, length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String sha256;

    @Column(name = "\"byteSize\"", nullable = false)
    private int byteSize;

    @Column(nullable = false)
    private byte[] payload;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected RegistrationSlip() {
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

    public String getRoundId() {
        return roundId;
    }

    public String getSha256() {
        return sha256;
    }

    public int getByteSize() {
        return byteSize;
    }

    public byte[] getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RegistrationSlip other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
