package io.campuscore.restfulapi.thesis.assistant.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistantMessageJpaRepository extends JpaRepository<AssistantMessageEntity, UUID> {
    List<AssistantMessageEntity> findByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId, Pageable pageable);

    @Query("select m from AssistantMessageEntity m join AssistantConversationEntity c "
            + "on c.id = m.conversationId where m.id = :messageId and c.ownerId = :ownerId")
    Optional<AssistantMessageEntity> findOwned(@Param("messageId") UUID messageId, @Param("ownerId") String ownerId);
}
