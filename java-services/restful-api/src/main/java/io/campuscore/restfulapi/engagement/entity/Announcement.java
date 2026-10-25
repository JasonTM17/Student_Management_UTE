package io.campuscore.restfulapi.engagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity mapping to engagement.Announcement.
 */
@Entity
@Table(name = "\"Announcement\"", schema = "engagement")
public class Announcement {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, length = 40)
    private String priority;

    @Column(name = "\"targetRoles\"", nullable = false, insertable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] targetRoles;

    @Column(name = "\"targetYears\"", nullable = false, insertable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private Integer[] targetYears;

    @Column(name = "\"targetDepartments\"", nullable = false, insertable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] targetDepartments;

    /**
     * Optional faculty scoping (V149): empty means every faculty; a student
     * matches through their curriculum's department, a lecturer through their
     * own department.
     */
    @Column(name = "\"targetFaculties\"", nullable = false, insertable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] targetFaculties;

    @Column(name = "\"isGlobal\"", nullable = false, insertable = false, updatable = false)
    private boolean isGlobal;

    @Column(name = "\"publishAt\"")
    private Instant publishAt;

    @Column(name = "\"expiresAt\"")
    private Instant expiresAt;

    @Column(name = "\"publishedBy\"", length = 120)
    private String publishedBy;

    @Column(name = "\"semesterId\"", length = 120)
    private String semesterId;

    @Column(name = "\"semesterName\"", length = 180)
    private String semesterName;

    @Column(name = "\"sectionId\"", length = 120)
    private String sectionId;

    @Column(name = "\"sectionNumber\"", length = 80)
    private String sectionNumber;

    @Column(name = "\"courseCode\"", length = 60)
    private String courseCode;

    @Column(name = "\"courseName\"", length = 240)
    private String courseName;

    @Column(name = "\"lecturerId\"", length = 120)
    private String lecturerId;

    @Column(name = "\"lecturerDisplayName\"", length = 240)
    private String lecturerDisplayName;

    @Column(name = "\"createdAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"updatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private int version;

    @Column(name = "\"archivedAt\"")
    private Instant archivedAt;

    @Column(name = "\"archivedBy\"", length = 120)
    private String archivedBy;

    @Column(name = "\"categoryId\"", length = 60)
    private String categoryId;

    @Column(nullable = false, length = 30, insertable = false, updatable = false)
    private String status;

    @Column(name = "\"coverImageUrl\"", length = 500)
    private String coverImageUrl;

    @Column(length = 500)
    private String summary;

    @Column(name = "\"readingTimeMinutes\"", nullable = false, insertable = false, updatable = false)
    private int readingTimeMinutes;

    @Column(name = "\"viewCount\"", nullable = false, insertable = false, updatable = false)
    private int viewCount;

    @Column(name = "\"uniqueReaderCount\"", nullable = false, insertable = false, updatable = false)
    private int uniqueReaderCount;

    @Column(length = 240)
    private String slug;

    @Column(name = "\"featuredOrder\"")
    private Integer featuredOrder;

    @Column(name = "\"displayOrder\"")
    private Integer displayOrder;

    @Column(name = "\"notifiedAt\"")
    private Instant notifiedAt;

    protected Announcement() {
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getPriority() {
        return priority;
    }

    public String[] getTargetRoles() {
        return targetRoles;
    }

    public Integer[] getTargetYears() {
        return targetYears;
    }

    public String[] getTargetDepartments() {
        return targetDepartments;
    }

    public String[] getTargetFaculties() {
        return targetFaculties;
    }

    public boolean isGlobal() {
        return isGlobal;
    }

    public Instant getPublishAt() {
        return publishAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public String getSemesterId() {
        return semesterId;
    }

    public String getSemesterName() {
        return semesterName;
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getSectionNumber() {
        return sectionNumber;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public String getCourseName() {
        return courseName;
    }

    public String getLecturerId() {
        return lecturerId;
    }

    public String getLecturerDisplayName() {
        return lecturerDisplayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public String getArchivedBy() {
        return archivedBy;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getStatus() {
        return status;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public String getSummary() {
        return summary;
    }

    public int getReadingTimeMinutes() {
        return readingTimeMinutes;
    }

    public int getViewCount() {
        return viewCount;
    }

    public int getUniqueReaderCount() {
        return uniqueReaderCount;
    }

    public String getSlug() {
        return slug;
    }

    public Integer getFeaturedOrder() {
        return featuredOrder;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public Instant getNotifiedAt() {
        return notifiedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Announcement other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
