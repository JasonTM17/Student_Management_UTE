package io.campuscore.restfulapi.academic.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RegistrationSlipRepository extends JpaRepository<RegistrationSlipEntity, String> {
    Optional<RegistrationSlipEntity> findByStudentIdAndRoundId(String studentId, String roundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RegistrationSlipEntity> findLockedByStudentIdAndRoundId(String studentId, String roundId);
}
