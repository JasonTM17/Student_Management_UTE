package io.campuscore.restfulapi.site.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to site.Appearance.
 */
@Entity
@Table(name = "\"Appearance\"", schema = "site")
public class Appearance {

    @Id
    @Column(nullable = false, length = 40)
    private String id;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "\"updatedBy\"", length = 255)
    private String updatedBy;

    @Column(name = "\"updatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Appearance() {
    }

    public String getId() {
        return id;
    }

    public String getPayload() {
        return payload;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Appearance other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
