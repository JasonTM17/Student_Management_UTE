package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AssistantFeedbackJpaRepository extends JpaRepository<AssistantFeedbackEntity, AssistantFeedbackId> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AssistantFeedbackEntity> findLockedById(AssistantFeedbackId id);
}
