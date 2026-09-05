package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.domain.TopicStatus;
import io.campuscore.restfulapi.thesis.service.ThesisTopicService;
import io.campuscore.restfulapi.thesis.web.ThesisTopicDtos.TopicResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("persistence")
@RequestMapping("/api/v1/thesis/topics")
public class ThesisTopicController {

    private final ThesisTopicService topics;

    public ThesisTopicController(ThesisTopicService topics) {
        this.topics = topics;
    }

    @GetMapping
    public List<TopicResponse> list(
            @RequestParam UUID roundId,
            @RequestParam(required = false) TopicStatus status) {
        return topics.list(roundId, status);
    }

    @GetMapping("/{id}")
    public TopicResponse get(@PathVariable UUID id) {
        return topics.get(id);
    }
}
