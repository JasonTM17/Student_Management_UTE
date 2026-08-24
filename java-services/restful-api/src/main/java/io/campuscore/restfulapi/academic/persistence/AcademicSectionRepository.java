package io.campuscore.restfulapi.academic.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AcademicSectionRepository extends JpaRepository<AcademicSectionEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AcademicSectionEntity> findLockedById(String id);
}
