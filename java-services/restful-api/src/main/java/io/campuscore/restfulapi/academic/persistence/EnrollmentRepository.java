package io.campuscore.restfulapi.academic.persistence;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<EnrollmentEntity, String> {
    List<EnrollmentEntity> findByStudentIdAndSemesterIdOrderByEnrolledAtAsc(String studentId, String semesterId);

    Optional<EnrollmentEntity> findByStudentIdAndSectionIdAndStatusIn(String studentId, String sectionId,
                                                                        Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EnrollmentEntity> findLockedById(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EnrollmentEntity e where e.studentId = :studentId and e.semesterId = :semesterId and e.status in :statuses order by e.id")
    List<EnrollmentEntity> findLockedStudentEnrollments(@Param("studentId") String studentId,
                                                         @Param("semesterId") String semesterId,
                                                         @Param("statuses") Collection<String> statuses);
}
