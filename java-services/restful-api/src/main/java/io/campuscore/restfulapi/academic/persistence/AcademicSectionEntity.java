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
    static AcademicSectionEntity snapshot(String id, int capacity, int enrolledCount, String status) {
        AcademicSectionEntity entity = new AcademicSectionEntity();
        entity.id = id;
        entity.capacity = capacity;
        entity.enrolledCount = enrolledCount;
        entity.status = status;
        return entity;
    }
    public void incrementEnrollment() {
        if (enrolledCount < 0) throw new IllegalStateException("INVALID_ENROLLED_COUNT");
        if (enrolledCount >= capacity) throw new IllegalStateException("SECTION_FULL");
        enrolledCount++;
    }
    public void decrementEnrollment() {
        if (enrolledCount <= 0) throw new IllegalStateException("SECTION_COUNT_UNDERFLOW");
        enrolledCount--;
    }
    public String getId() { return id; }
    public int getCapacity() { return capacity; }
    public int getEnrolledCount() { return enrolledCount; }
    public String getStatus() { return status; }
    public long getVersion() { return version; }
}
