package io.campuscore.restfulapi.academic.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalTime;

@Entity
@Table(name = "SectionSchedule", schema = "academic")
public class SectionScheduleEntity {
    @Id private String id;
    @Column(name = "sectionId", nullable = false) private String sectionId;
    @Column(name = "classroomId", nullable = false) private String classroomId;
    @Column(name = "dayOfWeek", nullable = false) private short dayOfWeek;
    @Column(name = "startTimeValue", nullable = false) private LocalTime startTime;
    @Column(name = "endTimeValue", nullable = false) private LocalTime endTime;
    @Version @Column(name = "version") private long version;
    protected SectionScheduleEntity() { }
    public String getId() { return id; }
    public String getSectionId() { return sectionId; }
    public String getClassroomId() { return classroomId; }
    public short getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public long getVersion() { return version; }
}
