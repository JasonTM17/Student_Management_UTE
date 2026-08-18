package io.campuscore.engagement.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "support_ticket", schema = "engagement")
public class SupportTicket {

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum Status {
        OPEN, IN_PROGRESS, RESOLVED, CLOSED
    }

    @Id
    private UUID id;

    @Column(name = "ticket_number", nullable = false, unique = true, length = 20)
    private String ticketNumber;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_email", nullable = false, length = 180)
    private String userEmail;

    @Column(name = "user_display_name", length = 180)
    private String userDisplayName;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 40)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "assigned_to_display_name", length = 180)
    private String assignedToDisplayName;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "closed_at")
    private Instant closedAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<TicketResponse> responses = new ArrayList<>();

    protected SupportTicket() {
    }

    public SupportTicket(UUID userId, String userEmail, String userDisplayName, String subject,
                         String description, String category, Priority priority) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.userEmail = userEmail;
        this.userDisplayName = userDisplayName;
        this.subject = subject;
        this.description = description;
        this.category = category;
        this.priority = priority == null ? Priority.MEDIUM : priority;
        this.status = Status.OPEN;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (ticketNumber == null) {
            ticketNumber = "TKT-" + id.toString().substring(0, 8).toUpperCase();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void assign(UUID assigneeId, String assigneeName) {
        this.assignedTo = assigneeId;
        this.assignedToDisplayName = assigneeName;
        this.status = Status.IN_PROGRESS;
    }

    public void addResponse(TicketResponse response) {
        response.setTicket(this);
        responses.add(response);
    }

    public UUID getId() {
        return id;
    }

    public String getTicketNumber() {
        return ticketNumber;
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

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public Priority getPriority() {
        return priority;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getAssignedTo() {
        return assignedTo;
    }

    public String getAssignedToDisplayName() {
        return assignedToDisplayName;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<TicketResponse> getResponses() {
        return responses;
    }
}
