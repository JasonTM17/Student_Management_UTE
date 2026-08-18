package io.campuscore.thesis.repository;

import io.campuscore.thesis.domain.ThesisGroupMember;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface ThesisGroupMemberRepository extends JpaRepository<ThesisGroupMember, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select member from ThesisGroupMember member where member.groupId = :groupId order by member.memberOrder")
    List<ThesisGroupMember> findAllByGroupIdForUpdate(UUID groupId);

    boolean existsByRoundIdAndStudentId(UUID roundId, UUID studentId);

    List<ThesisGroupMember> findAllByGroupIdOrderByMemberOrder(UUID groupId);
}
