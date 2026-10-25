package io.campuscore.restfulapi.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to campuscore_auth.TwoFactorChallenge.
 */
@Entity
@Table(name = "\"TwoFactorChallenge\"", schema = "campuscore_auth")
public class TwoFactorChallenge {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"userId\"", nullable = false, length = 120)
    private String userId;

    @Column(nullable = false, length = 16)
    private String purpose;

    @Column(name = "\"codeHash\"", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "\"expiresAt\"", nullable = false)
    private Instant expiresAt;

    @Column(name = "\"consumedAt\"")
    private Instant consumedAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private int attempts;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected TwoFactorChallenge() {
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TwoFactorChallenge other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
