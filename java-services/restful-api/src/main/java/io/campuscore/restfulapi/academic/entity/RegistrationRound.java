package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to academic.RegistrationRound.
 */
@Entity
@Table(name = "\"RegistrationRound\"", schema = "academic")
public class RegistrationRound {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"semesterId\"", nullable = false, length = 120)
    private String semesterId;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(nullable = false, length = 40)
    private String kind;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "\"windowStart\"", nullable = false)
    private Instant windowStart;

    @Column(name = "\"windowEnd\"", nullable = false)
    private Instant windowEnd;

    @Column(name = "\"creditLimit\"", nullable = false, insertable = false, updatable = false)
    private int creditLimit;

    @Column(nullable = false, insertable = false, updatable = false)
    private int version;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"updatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected RegistrationRound() {
    }

    public String getId() {
        return id;
    }

    public String getSemesterId() {
        return semesterId;
    }

    public String getName() {
        return name;
    }

    public String getKind() {
        return kind;
    }

    public String getStatus() {
        return status;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public int getCreditLimit() {
        return creditLimit;
    }

    public int getVersion() {
        return version;
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
        if (!(o instanceof RegistrationRound other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
