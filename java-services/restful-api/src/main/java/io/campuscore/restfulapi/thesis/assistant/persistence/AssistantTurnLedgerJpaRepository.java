package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistantTurnLedgerJpaRepository extends JpaRepository<AssistantTurnLedgerEntity, UUID> {
    Optional<AssistantTurnLedgerEntity> findByOwnerIdAndClientRequestId(String ownerId, UUID clientRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AssistantTurnLedgerEntity t where t.turnId = :turnId and t.ownerId = :ownerId")
    Optional<AssistantTurnLedgerEntity> findLockedByTurnIdAndOwnerId(
            @Param("turnId") UUID turnId, @Param("ownerId") String ownerId);
}
