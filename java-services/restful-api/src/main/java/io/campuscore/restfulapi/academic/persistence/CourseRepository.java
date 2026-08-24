package io.campuscore.restfulapi.academic.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<CourseEntity, String> {
    Optional<CourseEntity> findByCode(String code);
}
