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
 * JPA entity mapping to academic.RegistrationIdempotency.
 */
@Entity
@Table(name = "\"RegistrationIdempotency\"", schema = "academic")
public class RegistrationIdempotency {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"ownerId\"", nullable = false, length = 120)
    private String ownerId;

    @Column(name = "\"idempotencyKey\"", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "\"requestHash\"", nullable = false, length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String requestHash;

    @Column(nullable = false, length = 40)
    private String state;

    @Column(name = "\"enrollmentId\"", length = 120)
    private String enrollmentId;

    @Column(name = "\"responseBody\"", columnDefinition = "text")
    private String responseBody;

    @Column(name = "\"slipSha256\"", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String slipSha256;

    @Column(nullable = false, insertable = false, updatable = false)
    private int version;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"updatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected RegistrationIdempotency() {
    }

    public String getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getState() {
        return state;
    }

    public String getEnrollmentId() {
        return enrollmentId;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getSlipSha256() {
        return slipSha256;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RegistrationIdempotency other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
