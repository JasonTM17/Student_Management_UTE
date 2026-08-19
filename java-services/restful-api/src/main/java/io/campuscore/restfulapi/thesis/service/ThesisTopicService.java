package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.domain.TopicStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicRepository;
import io.campuscore.restfulapi.thesis.web.ThesisTopicDtos.TopicResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.thesis-read", name = "enabled", havingValue = "true")
public class ThesisTopicService {

    private final ThesisTopicRepository topics;

    public ThesisTopicService(ThesisTopicRepository topics) {
        this.topics = topics;
    }

    @Transactional(readOnly = true)
    public List<TopicResponse> list(UUID roundId, TopicStatus status) {
        TopicStatus requestedStatus = status == null ? TopicStatus.PUBLISHED : status;
        return topics.findAllByRoundIdAndStatusOrderByTitle(roundId, requestedStatus)
                .stream()
                .map(TopicResponse::from)
                .toList();
    }
}
