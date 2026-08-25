package io.campuscore.restfulapi.thesis.assistant.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantKnowledgeAuditJpaRepository extends JpaRepository<AssistantKnowledgeAuditEntity, UUID> {
    List<AssistantKnowledgeAuditEntity> findByRevisionIdOrderByCreatedAtAsc(UUID revisionId);
}
