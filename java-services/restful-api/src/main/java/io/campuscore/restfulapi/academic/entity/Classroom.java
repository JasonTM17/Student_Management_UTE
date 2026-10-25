package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to academic.Classroom.
 */
@Entity
@Table(name = "\"Classroom\"", schema = "academic")
public class Classroom {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(nullable = false, length = 160)
    private String building;

    @Column(name = "\"roomNumber\"", nullable = false, length = 80)
    private String roomNumber;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false, length = 80)
    private String type;

    @Column(name = "\"isActive\"", nullable = false, insertable = false, updatable = false)
    private boolean isActive;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"updatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Classroom() {
    }

    public String getId() {
        return id;
    }

    public String getBuilding() {
        return building;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getType() {
        return type;
    }

    public boolean isActive() {
        return isActive;
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
        if (!(o instanceof Classroom other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
