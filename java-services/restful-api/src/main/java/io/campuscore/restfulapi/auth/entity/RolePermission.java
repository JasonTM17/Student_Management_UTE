package io.campuscore.restfulapi.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * JPA entity mapping to campuscore_auth.RolePermission.
 */
@Entity
@Table(name = "\"RolePermission\"", schema = "campuscore_auth")
public class RolePermission {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"roleId\"", nullable = false, length = 120)
    private String roleId;

    @Column(name = "\"permissionId\"", nullable = false, length = 120)
    private String permissionId;

    protected RolePermission() {
    }

    public String getId() {
        return id;
    }

    public String getRoleId() {
        return roleId;
    }

    public String getPermissionId() {
        return permissionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RolePermission other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
