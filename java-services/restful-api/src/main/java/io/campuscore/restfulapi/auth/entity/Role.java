package io.campuscore.restfulapi.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA entity mapping to campuscore_auth.Role.
 */
@Entity
@Table(name = "\"Role\"", schema = "campuscore_auth")
public class Role {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "\"isSystem\"", nullable = false, insertable = false, updatable = false)
    private boolean isSystem;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "\"updatedAt\"", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected Role() {
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystem() {
        return isSystem;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Role other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
