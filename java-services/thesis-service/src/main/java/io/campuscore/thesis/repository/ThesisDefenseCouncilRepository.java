package io.campuscore.thesis.repository;

import io.campuscore.thesis.domain.ThesisDefenseCouncil;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ThesisDefenseCouncilRepository extends JpaRepository<ThesisDefenseCouncil, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select council from ThesisDefenseCouncil council where council.id = :id")
    ThesisDefenseCouncil findByIdForUpdate(UUID id);

    List<ThesisDefenseCouncil> findAllByRoundIdOrderByScheduledAtAsc(UUID roundId);
}
