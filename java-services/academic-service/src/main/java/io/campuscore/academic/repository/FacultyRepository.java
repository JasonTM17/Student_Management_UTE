package io.campuscore.academic.repository;

import io.campuscore.academic.domain.Faculty;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacultyRepository extends JpaRepository<Faculty, UUID> {

    Optional<Faculty> findByCode(String code);

    List<Faculty> findAllByOrderByNameAsc();

    boolean existsByCode(String code);
}
