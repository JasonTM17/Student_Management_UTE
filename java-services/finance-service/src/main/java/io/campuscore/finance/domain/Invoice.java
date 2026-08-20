package io.campuscore.finance.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoice", schema = "finance")
public class Invoice {

    @Id
    private UUID id;

    @Column(name = "invoice_number", nullable = false, unique = true)
    private String invoiceNumber;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "student_user_id", nullable = false)
    private UUID studentUserId;

    @Column(name = "student_display_name", nullable = false)
    private String studentDisplayName;

    @Column(name = "student_email", nullable = false)
    private String studentEmail;

    @Column(name = "student_code", nullable = false)
    private String studentCode;

    @Column(name = "semester_id", nullable = false)
    private UUID semesterId;

    @Column(name = "semester_name", nullable = false)
    private String semesterName;

    @Column(name = "semester_name_en")
    private String semesterNameEn;

    @Column(name = "semester_name_vi")
    private String semesterNameVi;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "due_date", nullable = false)
    private Instant dueDate;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(columnDefinition = "text")
    private String notes;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceItem> items = new ArrayList<>();

    protected Invoice() {
    }

    public Invoice(String invoiceNumber, UUID studentId, UUID studentUserId, String studentDisplayName,
                   String studentEmail, String studentCode, UUID semesterId, String semesterName,
                   BigDecimal subtotal, BigDecimal discount, BigDecimal total, Instant dueDate) {
        this.id = UUID.randomUUID();
        this.invoiceNumber = invoiceNumber;
        this.studentId = studentId;
        this.studentUserId = studentUserId;
        this.studentDisplayName = studentDisplayName;
        this.studentEmail = studentEmail;
        this.studentCode = studentCode;
        this.semesterId = semesterId;
        this.semesterName = semesterName;
        this.status = "DRAFT";
        this.subtotal = subtotal;
        this.discount = discount;
        this.total = total;
        this.dueDate = dueDate;
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
    public String getInvoiceNumber() { return invoiceNumber; }
    public UUID getStudentId() { return studentId; }
    public UUID getStudentUserId() { return studentUserId; }
    public String getStudentDisplayName() { return studentDisplayName; }
    public String getStudentEmail() { return studentEmail; }
    public String getStudentCode() { return studentCode; }
    public UUID getSemesterId() { return semesterId; }
    public String getSemesterName() { return semesterName; }
    public String getSemesterNameEn() { return semesterNameEn; }
    public String getSemesterNameVi() { return semesterNameVi; }
    public String getStatus() { return status; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscount() { return discount; }
    public BigDecimal getTotal() { return total; }
    public Instant getDueDate() { return dueDate; }
    public Instant getPaidAt() { return paidAt; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<InvoiceItem> getItems() { return items; }

    public void updateFields(String status, BigDecimal subtotal, BigDecimal discount, BigDecimal total,
                             Instant dueDate, Instant paidAt, String notes) {
        this.status = status;
        this.subtotal = subtotal;
        this.discount = discount;
        this.total = total;
        this.dueDate = dueDate;
        this.paidAt = paidAt;
        this.notes = notes;
    }
}
