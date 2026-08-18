package io.campuscore.finance.domain;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_intent", schema = "finance")
public class PaymentIntent {

    @Id
    private UUID id;

    @Column(name = "intent_number", nullable = false, unique = true)
    private String intentNumber;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", insertable = false, updatable = false)
    private Invoice invoice;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected PaymentIntent() {
    }

    public PaymentIntent(String intentNumber, UUID invoiceId, UUID studentId, String provider,
                         BigDecimal amount, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.intentNumber = intentNumber;
        this.invoiceId = invoiceId;
        this.studentId = studentId;
        this.provider = provider;
        this.status = "REQUIRES_ACTION";
        this.amount = amount;
        this.currency = "VND";
        this.expiresAt = expiresAt;
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
    public String getIntentNumber() { return intentNumber; }
    public UUID getInvoiceId() { return invoiceId; }
    public UUID getStudentId() { return studentId; }
    public String getProvider() { return provider; }
    public String getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getFinalizedAt() { return finalizedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateFields(String status, Instant finalizedAt) {
        this.status = status;
        this.finalizedAt = finalizedAt;
    }
}
