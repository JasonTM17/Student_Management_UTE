package io.campuscore.restfulapi.academic.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "Course", schema = "academic")
public class CourseEntity {
    @Id private String id;
    @Column(name = "code", nullable = false, length = 60) private String code;
    @Column(name = "credits", nullable = false) private int credits;
    @Version @Column(name = "version") private long version;
    protected CourseEntity() { }
    public String getId() { return id; }
    public String getCode() { return code; }
    public int getCredits() { return credits; }
    public long getVersion() { return version; }
}
