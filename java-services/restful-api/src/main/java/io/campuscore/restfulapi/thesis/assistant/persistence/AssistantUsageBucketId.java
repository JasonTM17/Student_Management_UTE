package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Embeddable
public class AssistantUsageBucketId implements Serializable {
    @Column(name = "bucket_date", nullable = false)
    private LocalDate bucketDate;
    @Column(name = "owner_id", nullable = false, length = 120)
    private String ownerId;
    @Column(name = "scope", nullable = false, length = 16)
    private String scope;

    protected AssistantUsageBucketId() { }

    public AssistantUsageBucketId(LocalDate bucketDate, String ownerId, String scope) {
        this.bucketDate = Objects.requireNonNull(bucketDate, "bucketDate");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    public LocalDate getBucketDate() { return bucketDate; }
    public String getOwnerId() { return ownerId; }
    public String getScope() { return scope; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AssistantUsageBucketId that)) return false;
        return Objects.equals(bucketDate, that.bucketDate)
                && Objects.equals(ownerId, that.ownerId)
                && Objects.equals(scope, that.scope);
    }

    @Override
    public int hashCode() { return Objects.hash(bucketDate, ownerId, scope); }
}
