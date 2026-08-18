package io.campuscore.academic.repository;

import io.campuscore.academic.domain.Course;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    Optional<Course> findByCode(String code);

    List<Course> findByDepartmentIdOrderByNameAsc(UUID departmentId);

    List<Course> findAllByOrderByNameAsc();

    boolean existsByCode(String code);
}
