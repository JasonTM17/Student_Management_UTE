package io.campuscore.restfulapi.thesis.dto;

import io.campuscore.restfulapi.thesis.entity.ThesisTopic;
import io.campuscore.restfulapi.thesis.model.TopicStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class ThesisTopicDtos {

    private ThesisTopicDtos() {
    }

    /**
     * Supervisor display row carried inside topic payloads. Names only — the
     * contact column stays on {@code GET /thesis/topics/{id}/supervisors},
     * which can authorize staff viewers for it, so this entry has no email
     * field at all.
     */
    public record TopicSupervisorEntry(
            String lecturerId,
            int supervisorOrder,
            String firstName,
            String lastName) {
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
            String resultReason,
            List<TopicSupervisorEntry> supervisors) {

        public static TopicResponse from(ThesisTopic topic, List<TopicSupervisorEntry> supervisors) {
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
                    topic.getResultReason(),
                    supervisors == null ? List.of() : supervisors);
        }
    }
}
