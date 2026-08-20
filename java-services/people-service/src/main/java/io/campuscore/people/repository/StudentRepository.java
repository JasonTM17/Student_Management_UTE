package io.campuscore.people.repository;

import io.campuscore.people.domain.Student;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    Optional<Student> findByStudentId(String studentId);

    Optional<Student> findByUserId(UUID userId);

    Page<Student> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Student> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    boolean existsByStudentId(String studentId);

    boolean existsByUserId(UUID userId);
}
