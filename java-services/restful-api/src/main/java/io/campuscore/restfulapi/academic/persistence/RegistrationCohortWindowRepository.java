package io.campuscore.restfulapi.academic.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationCohortWindowRepository extends JpaRepository<RegistrationCohortWindowEntity, String> {
    List<RegistrationCohortWindowEntity> findByRoundIdOrderByPriorityRankAscWindowStartAsc(String roundId);
}
