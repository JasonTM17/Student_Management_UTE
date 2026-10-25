package io.campuscore.restfulapi.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA entity mapping to campuscore_auth.Permission.
 */
@Entity
@Table(name = "\"Permission\"", schema = "campuscore_auth")
public class Permission {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 80)
    private String module;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Permission() {
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

    public String getModule() {
        return module;
    }

    public String getAction() {
        return action;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Permission other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
