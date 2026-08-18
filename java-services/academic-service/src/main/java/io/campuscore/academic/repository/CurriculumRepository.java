package io.campuscore.academic.repository;

import io.campuscore.academic.domain.Curriculum;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurriculumRepository extends JpaRepository<Curriculum, UUID> {

    Optional<Curriculum> findByCode(String code);

    List<Curriculum> findByDepartmentIdOrderByNameAsc(UUID departmentId);

    List<Curriculum> findAllByOrderByNameAsc();

    boolean existsByCode(String code);
}
