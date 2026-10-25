package io.campuscore.restfulapi.thesis.assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;

/**
 * JPA entity mapping to assistant.usage_bucket.
 */
@Entity
@IdClass(UsageBucketId.class)
@Table(name = "usage_bucket", schema = "assistant")
public class UsageBucket {

    @Id
    @Column(name = "bucket_date", nullable = false)
    private LocalDate bucketDate;

    @Id
    @Column(name = "owner_id", nullable = false, length = 120)
    private String ownerId;

    @Id
    @Column(nullable = false, length = 16)
    private String scope;

    @Column(name = "request_count", nullable = false, insertable = false, updatable = false)
    private int requestCount;

    protected UsageBucket() {
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

    public int getRequestCount() {
        return requestCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UsageBucket other)) {
            return false;
        }
        return Objects.equals(bucketDate, other.bucketDate) && Objects.equals(ownerId, other.ownerId) && Objects.equals(scope, other.scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bucketDate, ownerId, scope);
    }
}
