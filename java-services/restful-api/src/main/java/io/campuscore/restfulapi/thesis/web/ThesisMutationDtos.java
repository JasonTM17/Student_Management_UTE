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
            Instant lecturerSubmitStart,
            Instant lecturerSubmitEnd,
            Instant registrationStart,
            Instant registrationEnd,
            Instant gvpbDeadline,
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

    /**
     * Non-destructive report edit. Absent fields keep the stored value;
     * {@code clearUrl} explicitly removes the link and is only legal while a
     * file artifact remains (the report must keep at least one artifact).
     */
    public record ReportUpdateRequest(String title, String note, String url, Boolean clearUrl) {
    }

    /** The audited report-deadline escape hatch for a REGISTRATION_CLOSED round. */
    public record ReportDeadlineUpdateRequest(Instant reportDeadline) {
    }

    /**
     * Consequence preview the admin reviews before confirming
     * close-registration: non-cancelled group counts by approval state, the
     * number of report rows already submitted in the round, and the effective
     * report deadline ({@code report_deadline} override, else
     * {@code gvpb_deadline}) with its passed state.
     */
    public record CloseRegistrationPreviewResponse(
            long groupCount,
            long approvedGroupCount,
            long pendingGroupCount,
            long reportCount,
            Instant reportDeadline,
            boolean reportDeadlinePassed) {
    }

    /**
     * Minimal directory entry so a leader can invite a classmate into the
     * group. Deliberately carries no email: a directory lookup must not
     * expose contact PII to the whole student body.
     */
    public record StudentSearchResponse(
            String studentId,
            String studentNumber,
            String firstName,
            String lastName,
            String curriculumCode,
            String curriculumName) {
    }
}
