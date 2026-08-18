package io.campuscore.thesis.service;

import io.campuscore.thesis.domain.ThesisTopic;
import io.campuscore.thesis.domain.TopicStatus;
import io.campuscore.thesis.repository.ThesisTopicRepository;
import io.campuscore.thesis.web.DomainExceptions;
import io.campuscore.thesis.web.ThesisDtos.CreateTopicRequest;
import io.campuscore.thesis.web.ThesisDtos.TopicResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThesisTopicService {

    private final ThesisTopicRepository topics;
    private final ThesisRoundService rounds;

    public ThesisTopicService(ThesisTopicRepository topics, ThesisRoundService rounds) {
        this.topics = topics;
        this.rounds = rounds;
    }

    @Transactional
    public TopicResponse create(CreateTopicRequest request, UUID actorId) {
        rounds.get(request.roundId());
        if (request.maxGroups() < 1 || request.maxGroups() > 20) {
            throw new DomainExceptions.Conflict("A topic must allow between 1 and 20 groups");
        }
        return TopicResponse.from(topics.save(new ThesisTopic(
                request.roundId(),
                request.departmentId(),
                request.title().trim(),
                request.description().trim(),
                request.maxGroups(),
                actorId)));
    }

    @Transactional(readOnly = true)
    public List<TopicResponse> list(UUID roundId, TopicStatus status) {
        rounds.get(roundId);
        TopicStatus requestedStatus = status == null ? TopicStatus.PUBLISHED : status;
        return topics.findAllByRoundIdAndStatusOrderByTitle(roundId, requestedStatus)
                .stream()
                .map(TopicResponse::from)
                .toList();
    }

    @Transactional
    public TopicResponse publish(UUID id) {
        ThesisTopic topic = get(id);
        topic.publish();
        return TopicResponse.from(topics.save(topic));
    }

    @Transactional(readOnly = true)
    public ThesisTopic get(UUID id) {
        return topics.findById(id)
                .orElseThrow(() -> new DomainExceptions.NotFound("Thesis topic not found"));
    }
}
