package io.campuscore.people.repository;

import io.campuscore.people.domain.Lecturer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LecturerRepository extends JpaRepository<Lecturer, UUID> {

    Optional<Lecturer> findByEmployeeId(String employeeId);

    Optional<Lecturer> findByUserId(UUID userId);

    Page<Lecturer> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByEmployeeId(String employeeId);

    boolean existsByUserId(UUID userId);
}
