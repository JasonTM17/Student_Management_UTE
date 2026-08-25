package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AssistantProviderDispatchJpaRepository
        extends JpaRepository<AssistantProviderDispatchEntity, AssistantProviderDispatchId> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AssistantProviderDispatchEntity> findLockedById(AssistantProviderDispatchId id);
}
