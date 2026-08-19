package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.domain.ThesisTopic;
import io.campuscore.restfulapi.thesis.domain.TopicStatus;
import java.util.UUID;

public final class ThesisTopicDtos {

    private ThesisTopicDtos() {
    }

    public record TopicResponse(
            UUID id,
            UUID roundId,
            UUID departmentId,
            String title,
            String description,
            int maxGroups,
            TopicStatus status,
            UUID createdBy) {

        public static TopicResponse from(ThesisTopic topic) {
            return new TopicResponse(
                    topic.getId(),
                    topic.getRoundId(),
                    topic.getDepartmentId(),
                    topic.getTitle(),
                    topic.getDescription(),
                    topic.getMaxGroups(),
                    topic.getStatus(),
                    topic.getCreatedBy());
        }
    }
}
