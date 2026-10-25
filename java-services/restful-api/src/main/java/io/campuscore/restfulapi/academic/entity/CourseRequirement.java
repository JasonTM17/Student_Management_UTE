package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * JPA entity mapping to academic.CourseRequirement.
 */
@Entity
@Table(name = "\"CourseRequirement\"", schema = "academic")
public class CourseRequirement {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"courseId\"", nullable = false, length = 120)
    private String courseId;

    @Column(name = "\"requiredCourseId\"", nullable = false, length = 120)
    private String requiredCourseId;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(name = "\"minLetterGrade\"", length = 16)
    private String minLetterGrade;

    protected CourseRequirement() {
    }

    public String getId() {
        return id;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getRequiredCourseId() {
        return requiredCourseId;
    }

    public String getKind() {
        return kind;
    }

    public String getMinLetterGrade() {
        return minLetterGrade;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CourseRequirement other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
