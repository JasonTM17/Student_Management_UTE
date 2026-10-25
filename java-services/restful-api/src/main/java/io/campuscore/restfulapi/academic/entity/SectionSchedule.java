package io.campuscore.restfulapi.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * JPA entity mapping to academic.SectionSchedule.
 */
@Entity
@Table(name = "\"SectionSchedule\"", schema = "academic")
public class SectionSchedule {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "\"sectionId\"", nullable = false, length = 120)
    private String sectionId;

    @Column(name = "\"classroomId\"", nullable = false, length = 120)
    private String classroomId;

    @Column(name = "\"dayOfWeek\"", nullable = false)
    private int dayOfWeek;

    @Column(name = "\"startTime\"", nullable = false, length = 10)
    private String startTime;

    @Column(name = "\"endTime\"", nullable = false, length = 10)
    private String endTime;

    protected SectionSchedule() {
    }

    public String getId() {
        return id;
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getClassroomId() {
        return classroomId;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SectionSchedule other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
