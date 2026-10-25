package io.campuscore.restfulapi.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA entity mapping to campuscore_auth.User.
 */
@Entity
@Table(name = "\"User\"", schema = "campuscore_auth")
public class User {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(nullable = false, length = 200)
    private String password;

    @Column(name = "\"firstName\"", nullable = false, length = 120)
    private String firstName;

    @Column(name = "\"lastName\"", nullable = false, length = 120)
    private String lastName;

    @Column(length = 80)
    private String phone;

    @Column(length = 40)
    private String gender;

    @Column(name = "\"dateOfBirth\"")
    private LocalDateTime dateOfBirth;

    @Column(length = 500)
    private String address;

    @Column(columnDefinition = "text")
    private String avatar;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "\"emailVerified\"", nullable = false, insertable = false, updatable = false)
    private boolean emailVerified;

    @Column(name = "\"isSuperAdmin\"", nullable = false, insertable = false, updatable = false)
    private boolean isSuperAdmin;

    @Column(name = "\"failedLoginAttempts\"", nullable = false, insertable = false, updatable = false)
    private int failedLoginAttempts;

    @Column(name = "\"lockedUntil\"")
    private LocalDateTime lockedUntil;

    @Column(name = "\"lastLoginAt\"")
    private LocalDateTime lastLoginAt;

    @Column(name = "\"passwordChangedAt\"")
    private LocalDateTime passwordChangedAt;

    @Column(name = "\"refreshToken\"", length = 200)
    private String refreshToken;

    @Column(name = "\"resetToken\"", length = 200)
    private String resetToken;

    @Column(name = "\"resetExpires\"")
    private LocalDateTime resetExpires;

    @Column(name = "\"verificationToken\"", length = 200)
    private String verificationToken;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "\"updatedAt\"", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "\"mustChangePassword\"", nullable = false, insertable = false, updatable = false)
    private boolean mustChangePassword;

    @Column(name = "\"twoFactorEnabled\"", nullable = false, insertable = false, updatable = false)
    private boolean twoFactorEnabled;

    @Column(name = "\"credentialVersion\"", nullable = false, insertable = false, updatable = false)
    private long credentialVersion;

    protected User() {
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPhone() {
        return phone;
    }

    public String getGender() {
        return gender;
    }

    public LocalDateTime getDateOfBirth() {
        return dateOfBirth;
    }

    public String getAddress() {
        return address;
    }

    public String getAvatar() {
        return avatar;
    }

    public String getStatus() {
        return status;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public boolean isSuperAdmin() {
        return isSuperAdmin;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getResetToken() {
        return resetToken;
    }

    public LocalDateTime getResetExpires() {
        return resetExpires;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    public long getCredentialVersion() {
        return credentialVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
