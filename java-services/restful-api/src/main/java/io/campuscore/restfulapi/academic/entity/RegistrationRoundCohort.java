package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * JPA entity mapping to academic.RegistrationRoundCohort.
 */
@Entity
@Table(name = "\"RegistrationRoundCohort\"", schema = "academic")
public class RegistrationRoundCohort {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"roundId\"", nullable = false, length = 120)
    private String roundId;

    @Column(name = "\"curriculumId\"", length = 120)
    private String curriculumId;

    @Column
    private Integer year;

    @Column(name = "\"departmentId\"", length = 120)
    private String departmentId;

    protected RegistrationRoundCohort() {
    }

    public String getId() {
        return id;
    }

    public String getRoundId() {
        return roundId;
    }

    public String getCurriculumId() {
        return curriculumId;
    }

    public Integer getYear() {
        return year;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RegistrationRoundCohort other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
