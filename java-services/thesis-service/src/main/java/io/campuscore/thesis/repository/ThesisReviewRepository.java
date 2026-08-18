package io.campuscore.thesis.repository;

import io.campuscore.thesis.domain.ThesisReview;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThesisReviewRepository extends JpaRepository<ThesisReview, UUID> {

    boolean existsByCouncilIdAndGroupIdAndReviewerId(UUID councilId, UUID groupId, UUID reviewerId);

    List<ThesisReview> findAllByCouncilIdAndGroupId(UUID councilId, UUID groupId);
}
