package io.campuscore.thesis.repository;

import io.campuscore.thesis.domain.ThesisResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThesisResultRepository extends JpaRepository<ThesisResult, UUID> {

    Optional<ThesisResult> findByGroupId(UUID groupId);
}
