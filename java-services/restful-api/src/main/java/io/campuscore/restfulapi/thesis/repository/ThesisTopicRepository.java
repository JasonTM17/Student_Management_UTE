package io.campuscore.restfulapi.thesis.repository;

import io.campuscore.restfulapi.thesis.entity.ThesisTopic;
import io.campuscore.restfulapi.thesis.model.TopicStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThesisTopicRepository extends JpaRepository<ThesisTopic, UUID> {

    List<ThesisTopic> findAllByRoundIdAndStatusOrderByTitle(UUID roundId, TopicStatus status);
}
