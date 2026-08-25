package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AssistantUsageBucketJpaRepository extends JpaRepository<AssistantUsageBucketEntity, AssistantUsageBucketId> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AssistantUsageBucketEntity> findLockedById(AssistantUsageBucketId id);
}
