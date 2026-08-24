package io.campuscore.restfulapi.academic.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentAuditRepository extends JpaRepository<EnrollmentAuditEntity, String> {
    List<EnrollmentAuditEntity> findByStudentIdOrderByCreatedAtDesc(String studentId);
}
