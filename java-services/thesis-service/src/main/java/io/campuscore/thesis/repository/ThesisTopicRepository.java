package io.campuscore.thesis.repository;

import io.campuscore.thesis.domain.ThesisTopic;
import io.campuscore.thesis.domain.TopicStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThesisTopicRepository extends JpaRepository<ThesisTopic, UUID> {

    List<ThesisTopic> findAllByRoundIdAndStatusOrderByTitle(UUID roundId, TopicStatus status);

    List<ThesisTopic> findAllByStatusOrderByTitle(TopicStatus status);

    long countByRoundIdAndId(UUID roundId, UUID id);
}
