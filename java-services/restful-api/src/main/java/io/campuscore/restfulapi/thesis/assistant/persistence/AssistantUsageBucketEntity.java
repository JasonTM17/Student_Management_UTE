package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Daily user/global quota bucket. */
@Entity
@Table(name = "usage_bucket", schema = "assistant")
public class AssistantUsageBucketEntity {
    @EmbeddedId
    private AssistantUsageBucketId id;
    @Column(name = "request_count", nullable = false)
    private int requestCount;

    protected AssistantUsageBucketEntity() { }

    public AssistantUsageBucketEntity(AssistantUsageBucketId id, int requestCount) {
        this.id = id;
        this.requestCount = requestCount;
    }

    public AssistantUsageBucketId getId() { return id; }
    public int getRequestCount() { return requestCount; }
    public void increment() { requestCount++; }
}
