package io.campuscore.restfulapi.academic.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationSlipRepository extends JpaRepository<RegistrationSlipEntity, String> {
    Optional<RegistrationSlipEntity> findByStudentIdAndRoundId(String studentId, String roundId);
}
