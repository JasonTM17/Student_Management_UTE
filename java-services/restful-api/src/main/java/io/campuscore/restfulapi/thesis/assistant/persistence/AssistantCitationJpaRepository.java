package io.campuscore.restfulapi.thesis.assistant.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantCitationJpaRepository extends JpaRepository<AssistantCitationEntity, UUID> {
    List<AssistantCitationEntity> findByMessageIdOrderByOrdinalAsc(UUID messageId);
}
