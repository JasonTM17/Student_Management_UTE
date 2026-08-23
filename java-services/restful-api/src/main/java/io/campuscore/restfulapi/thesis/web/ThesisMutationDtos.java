package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.domain.GroupStatus;
import java.time.Instant;
import java.util.UUID;

public final class ThesisMutationDtos {

    private ThesisMutationDtos() {
    }

    public record RoundCreateRequest(
            String name,
            String thesisType,
            Instant registrationStart,
            Instant registrationEnd,
            Instant proposalPublishAt,
            Instant reportDate,
            Instant defenseDate) {
    }

    public record TopicCreateRequest(
            UUID roundId,
            String departmentId,
            String title,
            String description,
            Integer maxGroups) {
    }

    public record TopicUpdateRequest(
            String departmentId,
            String title,
            String description,
            Integer maxGroups) {
    }

    public record GroupCreateRequest(UUID roundId) {
    }

    public record MemberRequest(String studentId) {
    }

    public record TopicAssignmentRequest(UUID topicId) {
    }

    public record ProgressRequest(GroupStatus status) {
    }
}
