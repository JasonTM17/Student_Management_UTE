package io.campuscore.restfulapi.academic.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRequirementRepository extends JpaRepository<CourseRequirementEntity, String> {
    List<CourseRequirementEntity> findByCourseIdOrderByRequirementTypeAscRequiredCourseIdAsc(String courseId);
}
