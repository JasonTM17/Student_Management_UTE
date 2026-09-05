package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.domain.TopicStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundReadPort;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicRepository;
import io.campuscore.restfulapi.thesis.web.ThesisTopicDtos.TopicResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("persistence")
public class ThesisTopicService {

    private final ThesisTopicRepository topics;
    private final ThesisRoundReadPort rounds;

    public ThesisTopicService(ThesisTopicRepository topics, ThesisRoundReadPort rounds) {
        this.topics = topics;
        this.rounds = rounds;
    }

    @Transactional(readOnly = true)
    public List<TopicResponse> list(UUID roundId, TopicStatus status) {
        rounds.requireExisting(roundId);
        TopicStatus requestedStatus = status == null ? TopicStatus.PUBLISHED : status;
        return topics.findAllByRoundIdAndStatusOrderByTitle(roundId, requestedStatus)
                .stream()
                .map(TopicResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TopicResponse get(UUID id) {
        return topics.findById(id)
                .map(TopicResponse::from)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Thesis topic not found"));
    }
}
