package io.campuscore.restfulapi.thesis.assistant.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite key for {@link ProviderDispatchRegistry}.
 */
public class ProviderDispatchRegistryId implements Serializable {

    private String ownerId;
    private UUID clientRequestId;
    private long leaseGeneration;

    public ProviderDispatchRegistryId() {
    }

    public ProviderDispatchRegistryId(String ownerId, UUID clientRequestId, long leaseGeneration) {
        this.ownerId = ownerId;
        this.clientRequestId = clientRequestId;
        this.leaseGeneration = leaseGeneration;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProviderDispatchRegistryId other)) {
            return false;
        }
        return Objects.equals(ownerId, other.ownerId) && Objects.equals(clientRequestId, other.clientRequestId) && Objects.equals(leaseGeneration, other.leaseGeneration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerId, clientRequestId, leaseGeneration);
    }
}
