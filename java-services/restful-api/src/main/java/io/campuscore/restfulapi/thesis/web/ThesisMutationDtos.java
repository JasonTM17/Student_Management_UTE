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
            Instant reportDate) {
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

    /**
     * Internal member: studentId of an active student profile. External member
     * (different department or school): studentId blank, displayName required.
     */
    public record MemberRequest(String studentId, String displayName, String contact) {
    }

    public record TopicAssignmentRequest(UUID topicId) {
    }

    public record ProgressRequest(GroupStatus status) {
    }

    public record GroupRejectionRequest(String reason) {
    }

    /** Minimal directory entry so a leader can invite a classmate into the group. */
    public record StudentSearchResponse(
            String studentId,
            String studentNumber,
            String email,
            String firstName,
            String lastName,
            String curriculumCode,
            String curriculumName) {
    }
}
