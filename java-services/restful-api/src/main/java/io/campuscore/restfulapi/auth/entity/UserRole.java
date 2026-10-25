package io.campuscore.restfulapi.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * JPA entity mapping to campuscore_auth.UserRole.
 */
@Entity
@Table(name = "\"UserRole\"", schema = "campuscore_auth")
public class UserRole {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"userId\"", nullable = false, length = 120)
    private String userId;

    @Column(name = "\"roleId\"", nullable = false, length = 120)
    private String roleId;

    protected UserRole() {
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getRoleId() {
        return roleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserRole other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
