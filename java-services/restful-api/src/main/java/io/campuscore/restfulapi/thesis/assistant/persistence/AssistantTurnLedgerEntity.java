package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Idempotency/lease ledger projection; state transitions remain CAS-owned. */
@Entity
@Table(name = "chat_turn_ledger", schema = "assistant")
public class AssistantTurnLedgerEntity {
    @Id @Column(name = "turn_id") private UUID turnId;
    @Column(name = "owner_id", nullable = false, length = 120) private String ownerId;
    @Column(name = "client_request_id", nullable = false) private UUID clientRequestId;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
    @Column(name = "conversation_id") private UUID conversationId;
    @Column(name = "state", nullable = false, length = 24) private String state;
    @Column(name = "lease_owner", length = 160) private String leaseOwner;
    @Column(name = "lease_generation", nullable = false) private long leaseGeneration;
    @Column(name = "lease_expires_at") private Instant leaseExpiresAt;
    @Column(name = "dispatched_at") private Instant dispatchedAt;
    @Column(name = "quota_reserved", nullable = false) private boolean quotaReserved;
    @Column(name = "result_message_id") private UUID resultMessageId;
    @Column(name = "terminal_reason", length = 48) private String terminalReason;
    @Column(name = "source_snapshot_hash", length = 64) private String sourceSnapshotHash;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "purged_at") private Instant purgedAt;
    @Column(name = "tombstone_until") private Instant tombstoneUntil;
    @Column(name = "created_conversation", nullable = false) private boolean createdConversation;

    protected AssistantTurnLedgerEntity() { }

    public UUID getTurnId() { return turnId; }
    public String getOwnerId() { return ownerId; }
    public UUID getClientRequestId() { return clientRequestId; }
    public String getRequestHash() { return requestHash; }
    public UUID getConversationId() { return conversationId; }
    public String getState() { return state; }
    public String getLeaseOwner() { return leaseOwner; }
    public long getLeaseGeneration() { return leaseGeneration; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public Instant getDispatchedAt() { return dispatchedAt; }
    public boolean isQuotaReserved() { return quotaReserved; }
    public UUID getResultMessageId() { return resultMessageId; }
    public String getTerminalReason() { return terminalReason; }
    public String getSourceSnapshotHash() { return sourceSnapshotHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPurgedAt() { return purgedAt; }
    public Instant getTombstoneUntil() { return tombstoneUntil; }
    public boolean isCreatedConversation() { return createdConversation; }
}
