package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapping to academic.Semester.
 */
@Entity
@Table(name = "\"Semester\"", schema = "academic")
public class Semester {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "\"nameEn\"", length = 180)
    private String nameEn;

    @Column(name = "\"nameVi\"", length = 180)
    private String nameVi;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(name = "\"academicYearId\"", nullable = false, length = 120)
    private String academicYearId;

    @Column(name = "\"startDate\"", nullable = false)
    private Instant startDate;

    @Column(name = "\"endDate\"", nullable = false)
    private Instant endDate;

    @Column(name = "\"registrationStart\"")
    private Instant registrationStart;

    @Column(name = "\"registrationEnd\"")
    private Instant registrationEnd;

    @Column(name = "\"addDropStart\"")
    private Instant addDropStart;

    @Column(name = "\"addDropEnd\"")
    private Instant addDropEnd;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"updatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Semester() {
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getNameVi() {
        return nameVi;
    }

    public String getType() {
        return type;
    }

    public String getAcademicYearId() {
        return academicYearId;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public Instant getRegistrationStart() {
        return registrationStart;
    }

    public Instant getRegistrationEnd() {
        return registrationEnd;
    }

    public Instant getAddDropStart() {
        return addDropStart;
    }

    public Instant getAddDropEnd() {
        return addDropEnd;
    }

    public String getStatus() {
        return status;
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
        if (!(o instanceof Semester other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
