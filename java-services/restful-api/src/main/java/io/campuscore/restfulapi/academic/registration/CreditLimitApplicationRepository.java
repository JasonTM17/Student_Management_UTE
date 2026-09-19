package io.campuscore.restfulapi.academic.registration;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for CreditLimitApplicationEntity.
 */
@Repository
public interface CreditLimitApplicationRepository extends JpaRepository<CreditLimitApplicationEntity, String> {

    Optional<CreditLimitApplicationEntity> findFirstByStudentIdAndRoundIdOrderByCreatedAtDesc(
            String studentId, String roundId);

    List<CreditLimitApplicationEntity> findByRoundIdOrderByCreatedAtDesc(String roundId);

    List<CreditLimitApplicationEntity> findByStatusOrderByCreatedAtDesc(String status);
}
