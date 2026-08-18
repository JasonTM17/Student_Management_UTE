package io.campuscore.engagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_response", schema = "engagement")
public class TicketResponse {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_email", nullable = false, length = 180)
    private String userEmail;

    @Column(name = "user_display_name", length = 180)
    private String userDisplayName;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "is_internal", nullable = false)
    private boolean internal;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TicketResponse() {
    }

    public TicketResponse(UUID userId, String userEmail, String userDisplayName, String message, boolean internal) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.userEmail = userEmail;
        this.userDisplayName = userDisplayName;
        this.message = message;
        this.internal = internal;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void setTicket(SupportTicket ticket) {
        this.ticket = ticket;
    }

    public UUID getId() {
        return id;
    }

    public SupportTicket getTicket() {
        return ticket;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getUserDisplayName() {
        return userDisplayName;
    }

    public String getMessage() {
        return message;
    }

    public boolean isInternal() {
        return internal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
