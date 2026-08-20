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
@Table(name = "curriculum", schema = "academic")
public class Curriculum {

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

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "department_code")
    private String departmentCode;

    @Column(name = "department_name")
    private String departmentName;

    private String description;

    @Column(nullable = false)
    private int credits;

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

    protected Curriculum() {
    }

    public Curriculum(String name, String code, UUID departmentId, int credits) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.code = code;
        this.departmentId = departmentId;
        this.credits = credits;
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
    public UUID getDepartmentId() { return departmentId; }
    public String getDepartmentCode() { return departmentCode; }
    public String getDepartmentName() { return departmentName; }
    public String getDescription() { return description; }
    public int getCredits() { return credits; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateFields(String name, String nameEn, String nameVi, String code,
                             UUID departmentId, String departmentCode, String departmentName,
                             String description, int credits, boolean active) {
        this.name = name;
        this.nameEn = nameEn;
        this.nameVi = nameVi;
        this.code = code;
        this.departmentId = departmentId;
        this.departmentCode = departmentCode;
        this.departmentName = departmentName;
        this.description = description;
        this.credits = credits;
        this.active = active;
    }
}
