package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Provider dispatch fence keyed by owner, request and lease generation. */
@Entity
@Table(name = "provider_dispatch_registry", schema = "assistant")
public class AssistantProviderDispatchEntity {
    @EmbeddedId
    private AssistantProviderDispatchId id;
    @Column(name = "provider_handle", length = 180) private String providerHandle;
    @Column(name = "state", nullable = false, length = 24) private String state;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;

    protected AssistantProviderDispatchEntity() { }

    public AssistantProviderDispatchEntity(AssistantProviderDispatchId id, String providerHandle, Instant now) {
        this.id = id;
        this.providerHandle = providerHandle;
        this.state = "DISPATCHED";
        this.createdAt = now;
    }

    public AssistantProviderDispatchId getId() { return id; }
    public String getProviderHandle() { return providerHandle; }
    public String getState() { return state; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}
