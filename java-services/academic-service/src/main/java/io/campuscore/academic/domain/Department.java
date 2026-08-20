package io.campuscore.academic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "department", schema = "academic")
public class Department {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "name_vi")
    private String nameVi;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "description_en", columnDefinition = "text")
    private String descriptionEn;

    @Column(name = "description_vi", columnDefinition = "text")
    private String descriptionVi;

    private String chair;

    private String phone;

    private String email;

    private String building;

    @Column(name = "faculty_id", nullable = false)
    private UUID facultyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id", insertable = false, updatable = false)
    private Faculty faculty;

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

    protected Department() {
    }

    public Department(String name, String code, UUID facultyId) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.code = code;
        this.facultyId = facultyId;
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
    public String getName() { return name; }
    public String getNameEn() { return nameEn; }
    public String getNameVi() { return nameVi; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public String getDescriptionEn() { return descriptionEn; }
    public String getDescriptionVi() { return descriptionVi; }
    public String getChair() { return chair; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getBuilding() { return building; }
    public UUID getFacultyId() { return facultyId; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateFields(String name, String nameEn, String nameVi, String code,
                             String description, String descriptionEn, String descriptionVi,
                             String chair, String phone, String email, String building,
                             UUID facultyId, boolean active) {
        this.name = name;
        this.nameEn = nameEn;
        this.nameVi = nameVi;
        this.code = code;
        this.description = description;
        this.descriptionEn = descriptionEn;
        this.descriptionVi = descriptionVi;
        this.chair = chair;
        this.phone = phone;
        this.email = email;
        this.building = building;
        this.facultyId = facultyId;
        this.active = active;
    }
}
