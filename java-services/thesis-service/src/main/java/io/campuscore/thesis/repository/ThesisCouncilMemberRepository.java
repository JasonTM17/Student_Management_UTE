package io.campuscore.thesis.repository;

import io.campuscore.thesis.domain.ThesisCouncilMember;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ThesisCouncilMemberRepository extends JpaRepository<ThesisCouncilMember, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select member from ThesisCouncilMember member where member.councilId = :councilId order by member.memberOrder")
    List<ThesisCouncilMember> findAllByCouncilIdForUpdate(UUID councilId);

    List<ThesisCouncilMember> findAllByCouncilIdOrderByMemberOrder(UUID councilId);

    boolean existsByCouncilIdAndLecturerId(UUID councilId, UUID lecturerId);

    long countByCouncilId(UUID councilId);
}
