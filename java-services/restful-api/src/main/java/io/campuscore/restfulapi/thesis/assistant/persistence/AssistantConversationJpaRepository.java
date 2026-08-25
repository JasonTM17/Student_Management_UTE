package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistantConversationJpaRepository extends JpaRepository<AssistantConversationEntity, UUID> {
    List<AssistantConversationEntity> findByOwnerIdAndStateNotOrderByUpdatedAtDesc(String ownerId, String state, Pageable pageable);

    Optional<AssistantConversationEntity> findByIdAndOwnerId(UUID id, String ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AssistantConversationEntity c where c.id = :id and c.ownerId = :ownerId")
    Optional<AssistantConversationEntity> findLockedByIdAndOwnerId(
            @Param("id") UUID id, @Param("ownerId") String ownerId);
}
