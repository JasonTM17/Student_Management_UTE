package io.campuscore.people.domain;

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
@Table(name = "lecturer", schema = "people")
public class Lecturer {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "department_code")
    private String departmentCode;

    @Column(name = "department_name")
    private String departmentName;

    @Column(name = "employee_id", nullable = false, unique = true)
    private String employeeId;

    @Column(length = 100)
    private String title;

    @Column(length = 200)
    private String specialization;

    @Column(length = 200)
    private String office;

    @Column(length = 30)
    private String phone;

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

    protected Lecturer() {
    }

    public Lecturer(UUID userId, String email, String firstName, String lastName,
                    UUID departmentId, String departmentCode, String departmentName,
                    String employeeId, String title, String specialization,
                    String office, String phone, boolean active) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.departmentId = departmentId;
        this.departmentCode = departmentCode;
        this.departmentName = departmentName;
        this.employeeId = employeeId;
        this.title = title;
        this.specialization = specialization;
        this.office = office;
        this.phone = phone;
        this.active = active;
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
    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public UUID getDepartmentId() { return departmentId; }
    public String getDepartmentCode() { return departmentCode; }
    public String getDepartmentName() { return departmentName; }
    public String getEmployeeId() { return employeeId; }
    public String getTitle() { return title; }
    public String getSpecialization() { return specialization; }
    public String getOffice() { return office; }
    public String getPhone() { return phone; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateFields(String email, String firstName, String lastName,
                             UUID departmentId, String departmentCode, String departmentName,
                             String employeeId, String title, String specialization,
                             String office, String phone, boolean active) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.departmentId = departmentId;
        this.departmentCode = departmentCode;
        this.departmentName = departmentName;
        this.employeeId = employeeId;
        this.title = title;
        this.specialization = specialization;
        this.office = office;
        this.phone = phone;
        this.active = active;
    }
}
