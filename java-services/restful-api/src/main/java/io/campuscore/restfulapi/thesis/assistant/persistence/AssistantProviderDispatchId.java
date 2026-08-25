package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AssistantProviderDispatchId implements Serializable {
    @Column(name = "owner_id", nullable = false, length = 120)
    private String ownerId;
    @Column(name = "client_request_id", nullable = false)
    private UUID clientRequestId;
    @Column(name = "lease_generation", nullable = false)
    private long leaseGeneration;

    protected AssistantProviderDispatchId() { }

    public AssistantProviderDispatchId(String ownerId, UUID clientRequestId, long leaseGeneration) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.clientRequestId = Objects.requireNonNull(clientRequestId, "clientRequestId");
        this.leaseGeneration = leaseGeneration;
    }

    public String getOwnerId() { return ownerId; }
    public UUID getClientRequestId() { return clientRequestId; }
    public long getLeaseGeneration() { return leaseGeneration; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AssistantProviderDispatchId that)) return false;
        return leaseGeneration == that.leaseGeneration
                && Objects.equals(ownerId, that.ownerId)
                && Objects.equals(clientRequestId, that.clientRequestId);
    }

    @Override
    public int hashCode() { return Objects.hash(ownerId, clientRequestId, leaseGeneration); }
}
