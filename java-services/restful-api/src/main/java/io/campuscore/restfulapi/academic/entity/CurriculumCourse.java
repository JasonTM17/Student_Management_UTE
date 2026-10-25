package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * JPA entity mapping to academic.CurriculumCourse.
 */
@Entity
@Table(name = "\"CurriculumCourse\"", schema = "academic")
public class CurriculumCourse {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"curriculumId\"", nullable = false, length = 120)
    private String curriculumId;

    @Column(name = "\"courseId\"", nullable = false, length = 120)
    private String courseId;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int semester;

    @Column(name = "\"isMandatory\"", nullable = false, insertable = false, updatable = false)
    private boolean isMandatory;

    protected CurriculumCourse() {
    }

    public String getId() {
        return id;
    }

    public String getCurriculumId() {
        return curriculumId;
    }

    public String getCourseId() {
        return courseId;
    }

    public int getYear() {
        return year;
    }

    public int getSemester() {
        return semester;
    }

    public boolean isMandatory() {
        return isMandatory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CurriculumCourse other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
