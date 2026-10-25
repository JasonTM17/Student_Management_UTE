package io.campuscore.restfulapi.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA entity mapping to campuscore_auth.Session.
 */
@Entity
@Table(name = "\"Session\"", schema = "campuscore_auth")
public class Session {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"userId\"", nullable = false, length = 120)
    private String userId;

    @Column(name = "\"refreshToken\"", nullable = false, length = 200)
    private String refreshToken;

    @Column(name = "\"userAgent\"", length = 500)
    private String userAgent;

    @Column(name = "\"ipAddress\"", length = 80)
    private String ipAddress;

    @Column(name = "\"expiresAt\"", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "\"previousRefreshToken\"", length = 200)
    private String previousRefreshToken;

    @Column(name = "\"rotatedAt\"")
    private LocalDateTime rotatedAt;

    protected Session() {
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getPreviousRefreshToken() {
        return previousRefreshToken;
    }

    public LocalDateTime getRotatedAt() {
        return rotatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Session other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
