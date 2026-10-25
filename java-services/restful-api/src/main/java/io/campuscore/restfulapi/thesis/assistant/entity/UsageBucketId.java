package io.campuscore.restfulapi.thesis.assistant.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Composite key for {@link UsageBucket}.
 */
public class UsageBucketId implements Serializable {

    private LocalDate bucketDate;
    private String ownerId;
    private String scope;

    public UsageBucketId() {
    }

    public UsageBucketId(LocalDate bucketDate, String ownerId, String scope) {
        this.bucketDate = bucketDate;
        this.ownerId = ownerId;
        this.scope = scope;
    }

    public LocalDate getBucketDate() {
        return bucketDate;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getScope() {
        return scope;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UsageBucketId other)) {
            return false;
        }
        return Objects.equals(bucketDate, other.bucketDate) && Objects.equals(ownerId, other.ownerId) && Objects.equals(scope, other.scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bucketDate, ownerId, scope);
    }
}
