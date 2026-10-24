package io.campuscore.restfulapi.thesis.dto;

import io.campuscore.restfulapi.thesis.entity.ThesisTopic;
import io.campuscore.restfulapi.thesis.model.TopicStatus;
import java.math.BigDecimal;
import java.util.UUID;

public final class ThesisTopicDtos {

    private ThesisTopicDtos() {
    }

    public record TopicResponse(
            UUID id,
            UUID roundId,
            String departmentId,
            String title,
            String description,
            int maxGroups,
            TopicStatus status,
            String createdBy,
            BigDecimal finalScore,
            String resultStatus,
            String gvpbLecturerId,
            String gvpbWaivedReason,
            String resultReason) {

        public static TopicResponse from(ThesisTopic topic) {
            return new TopicResponse(
                    topic.getId(),
                    topic.getRoundId(),
                    topic.getDepartmentId(),
                    topic.getTitle(),
                    topic.getDescription(),
                    topic.getMaxGroups(),
                    topic.getStatus(),
                    topic.getCreatedBy(),
                    topic.getFinalScore(),
                    topic.getResultStatus(),
                    topic.getGvpbLecturerId(),
                    topic.getGvpbWaivedReason(),
                    topic.getResultReason());
        }
    }
}
