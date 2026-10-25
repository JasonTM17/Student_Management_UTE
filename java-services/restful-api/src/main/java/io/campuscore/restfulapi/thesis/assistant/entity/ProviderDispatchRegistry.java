package io.campuscore.restfulapi.thesis.assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping to assistant.provider_dispatch_registry.
 */
@Entity
@IdClass(ProviderDispatchRegistryId.class)
@Table(name = "provider_dispatch_registry", schema = "assistant")
public class ProviderDispatchRegistry {

    @Id
    @Column(name = "owner_id", nullable = false, length = 120)
    private String ownerId;

    @Id
    @Column(name = "client_request_id", nullable = false)
    private UUID clientRequestId;

    @Id
    @Column(name = "lease_generation", nullable = false)
    private long leaseGeneration;

    @Column(name = "provider_handle", length = 180)
    private String providerHandle;

    @Column(nullable = false, length = 24, insertable = false, updatable = false)
    private String state;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected ProviderDispatchRegistry() {
    }

    public String getOwnerId() {
        return ownerId;
    }

    public UUID getClientRequestId() {
        return clientRequestId;
    }

    public long getLeaseGeneration() {
        return leaseGeneration;
    }

    public String getProviderHandle() {
        return providerHandle;
    }

    public String getState() {
        return state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProviderDispatchRegistry other)) {
            return false;
        }
        return Objects.equals(ownerId, other.ownerId) && Objects.equals(clientRequestId, other.clientRequestId) && Objects.equals(leaseGeneration, other.leaseGeneration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerId, clientRequestId, leaseGeneration);
    }
}
