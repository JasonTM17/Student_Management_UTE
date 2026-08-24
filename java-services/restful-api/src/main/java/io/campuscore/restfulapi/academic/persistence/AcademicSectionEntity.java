package io.campuscore.restfulapi.academic.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Minimal capacity aggregate; relationships remain DTO/service concerns. */
@Entity
@Table(name = "Section", schema = "academic")
public class AcademicSectionEntity {
    @Id private String id;
    @Column(name = "capacity", nullable = false) private int capacity;
    @Column(name = "enrolledCount", nullable = false) private int enrolledCount;
    @Column(name = "status", nullable = false, length = 40) private String status;
    @Version @Column(name = "version") private long version;
    protected AcademicSectionEntity() { }
    public String getId() { return id; }
    public int getCapacity() { return capacity; }
    public int getEnrolledCount() { return enrolledCount; }
    public String getStatus() { return status; }
    public long getVersion() { return version; }
}
