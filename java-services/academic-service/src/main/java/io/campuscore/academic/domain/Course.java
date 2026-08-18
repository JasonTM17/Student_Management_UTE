package io.campuscore.academic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course", schema = "academic")
public class Course {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "name_vi")
    private String nameVi;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "description_en", columnDefinition = "text")
    private String descriptionEn;

    @Column(name = "description_vi", columnDefinition = "text")
    private String descriptionVi;

    @Column(nullable = false)
    private int credits;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Course() {
    }

    public Course(String code, String name, int credits, UUID departmentId) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.credits = credits;
        this.departmentId = departmentId;
        this.active = true;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getNameEn() { return nameEn; }
    public String getNameVi() { return nameVi; }
    public String getDescription() { return description; }
    public String getDescriptionEn() { return descriptionEn; }
    public String getDescriptionVi() { return descriptionVi; }
    public int getCredits() { return credits; }
    public UUID getDepartmentId() { return departmentId; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateFields(String code, String name, String nameEn, String nameVi,
                             String description, String descriptionEn, String descriptionVi,
                             int credits, UUID departmentId, boolean active) {
        this.code = code;
        this.name = name;
        this.nameEn = nameEn;
        this.nameVi = nameVi;
        this.description = description;
        this.descriptionEn = descriptionEn;
        this.descriptionVi = descriptionVi;
        this.credits = credits;
        this.departmentId = departmentId;
        this.active = active;
    }
}
