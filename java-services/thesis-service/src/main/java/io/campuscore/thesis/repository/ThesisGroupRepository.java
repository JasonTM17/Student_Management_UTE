package io.campuscore.thesis.repository;

import io.campuscore.thesis.domain.ThesisGroup;
import java.util.List;
import java.util.UUID;
import java.util.Collection;
import io.campuscore.thesis.domain.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface ThesisGroupRepository extends JpaRepository<ThesisGroup, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select thesisGroup from ThesisGroup thesisGroup where thesisGroup.id = :id")
    ThesisGroup findByIdForUpdate(UUID id);

    List<ThesisGroup> findAllByRoundIdOrderByCreatedAtDesc(UUID roundId);

    boolean existsByRoundIdAndLeaderStudentId(UUID roundId, UUID leaderStudentId);

    long countByTopicIdAndApprovalStatusIn(UUID topicId, Collection<ApprovalStatus> statuses);
}
