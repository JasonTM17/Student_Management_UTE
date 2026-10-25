package io.campuscore.restfulapi.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to campuscore_auth.PasswordResetToken.
 */
@Entity
@Table(name = "\"PasswordResetToken\"", schema = "campuscore_auth")
public class PasswordResetToken {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"userId\"", nullable = false, length = 120)
    private String userId;

    @Column(name = "\"tokenHash\"", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "\"expiresAt\"", nullable = false)
    private Instant expiresAt;

    @Column(name = "\"usedAt\"")
    private Instant usedAt;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"requestIp\"", length = 64)
    private String requestIp;

    protected PasswordResetToken() {
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getRequestIp() {
        return requestIp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PasswordResetToken other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
