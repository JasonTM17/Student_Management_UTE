package io.campuscore.restfulapi.academic.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "CourseRequirement", schema = "academic")
public class CourseRequirementEntity {
    @Id private String id;
    @Column(name = "courseId", nullable = false) private String courseId;
    @Column(name = "requiredCourseId", nullable = false) private String requiredCourseId;
    @Column(name = "requirementType", nullable = false, length = 20) private String requirementType;
    protected CourseRequirementEntity() { }
    public String getId() { return id; }
    public String getCourseId() { return courseId; }
    public String getRequiredCourseId() { return requiredCourseId; }
    public String getRequirementType() { return requirementType; }
}
