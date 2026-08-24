package io.campuscore.restfulapi.academic.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RegistrationRoundRepository extends JpaRepository<RegistrationRoundEntity, String> {
    List<RegistrationRoundEntity> findBySemesterIdOrderByRegistrationStartDesc(String semesterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RegistrationRoundEntity> findLockedById(String id);
}
