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
@Table(name = "student", schema = "people")
public class Student {

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

    @Column(name = "student_id", nullable = false, unique = true)
    private String studentId;

    @Column(name = "curriculum_id", nullable = false)
    private UUID curriculumId;

    @Column(name = "curriculum_code")
    private String curriculumCode;

    @Column(name = "curriculum_name")
    private String curriculumName;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "department_code")
    private String departmentCode;

    @Column(name = "department_name")
    private String departmentName;

    @Column(name = "study_year", nullable = false)
    private int year;

    @Column(nullable = false, length = 20)
    private String status;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "admission_date", nullable = false)
    private Instant admissionDate;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Student() {
    }

    public Student(UUID userId, String email, String firstName, String lastName, String studentId,
                   UUID curriculumId, String curriculumCode, String curriculumName,
                   UUID departmentId, String departmentCode, String departmentName,
                   int year, String status, Instant admissionDate) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.studentId = studentId;
        this.curriculumId = curriculumId;
        this.curriculumCode = curriculumCode;
        this.curriculumName = curriculumName;
        this.departmentId = departmentId;
        this.departmentCode = departmentCode;
        this.departmentName = departmentName;
        this.year = year;
        this.status = status;
        this.admissionDate = admissionDate;
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
    public String getStudentId() { return studentId; }
    public UUID getCurriculumId() { return curriculumId; }
    public String getCurriculumCode() { return curriculumCode; }
    public String getCurriculumName() { return curriculumName; }
    public UUID getDepartmentId() { return departmentId; }
    public String getDepartmentCode() { return departmentCode; }
    public String getDepartmentName() { return departmentName; }
    public int getYear() { return year; }
    public String getStatus() { return status; }
    public Instant getAdmissionDate() { return admissionDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateFields(String email, String firstName, String lastName, String studentId,
                             UUID curriculumId, String curriculumCode, String curriculumName,
                             UUID departmentId, String departmentCode, String departmentName,
                             int year, String status, Instant admissionDate) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.studentId = studentId;
        this.curriculumId = curriculumId;
        this.curriculumCode = curriculumCode;
        this.curriculumName = curriculumName;
        this.departmentId = departmentId;
        this.departmentCode = departmentCode;
        this.departmentName = departmentName;
        this.year = year;
        this.status = status;
        this.admissionDate = admissionDate;
    }
}
