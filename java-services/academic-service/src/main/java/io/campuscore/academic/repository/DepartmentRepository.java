package io.campuscore.academic.repository;

import io.campuscore.academic.domain.Department;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    Optional<Department> findByCode(String code);

    List<Department> findByFacultyIdOrderByNameAsc(UUID facultyId);

    List<Department> findAllByOrderByNameAsc();

    boolean existsByCode(String code);
}
