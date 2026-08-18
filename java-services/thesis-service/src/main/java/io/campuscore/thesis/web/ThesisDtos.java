package io.campuscore.thesis.web;

import io.campuscore.thesis.domain.ApprovalStatus;
import io.campuscore.thesis.domain.GroupStatus;
import io.campuscore.thesis.domain.CouncilMemberRole;
import io.campuscore.thesis.domain.CouncilStatus;
import io.campuscore.thesis.domain.RoundStatus;
import io.campuscore.thesis.domain.ResultStatus;
import io.campuscore.thesis.domain.ThesisCouncilMember;
import io.campuscore.thesis.domain.ThesisDefenseCouncil;
import io.campuscore.thesis.domain.ThesisResult;
import io.campuscore.thesis.domain.ThesisGroup;
import io.campuscore.thesis.domain.ThesisGroupMember;
import io.campuscore.thesis.domain.ThesisRegistrationRound;
import io.campuscore.thesis.domain.ThesisTopic;
import io.campuscore.thesis.domain.TopicStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ThesisDtos {

    private ThesisDtos() {
    }

    public record CreateRoundRequest(
            @NotBlank @Size(max = 180) String name,
            @NotBlank @Size(max = 40) String thesisType,
            @NotNull Instant registrationStart,
            @NotNull Instant registrationEnd,
            Instant proposalPublishAt,
            Instant reportDate,
            Instant defenseDate) {
    }

    public record RoundResponse(
            UUID id,
            String name,
            String thesisType,
            Instant registrationStart,
            Instant registrationEnd,
            Instant proposalPublishAt,
            Instant reportDate,
            Instant defenseDate,
            RoundStatus status) {

        public static RoundResponse from(ThesisRegistrationRound round) {
            return new RoundResponse(
                    round.getId(),
                    round.getName(),
                    round.getThesisType(),
                    round.getRegistrationStart(),
                    round.getRegistrationEnd(),
                    round.getProposalPublishAt(),
                    round.getReportDate(),
                    round.getDefenseDate(),
                    round.getStatus());
        }
    }

    public record CreateTopicRequest(
            @NotNull UUID roundId,
            @NotNull UUID departmentId,
            @NotBlank @Size(max = 240) String title,
            @NotBlank @Size(max = 4000) String description,
            @NotNull Integer maxGroups) {
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

    public record CreateGroupRequest(@NotNull UUID roundId) {
    }

    public record AddMemberRequest(@NotNull UUID studentId) {
    }

    public record AssignTopicRequest(@NotNull UUID topicId) {
    }

    public record DecideGroupRequest(
            @NotNull Boolean approved,
            @Size(max = 500) String reason) {
    }

    public record GroupResponse(
            UUID id,
            UUID roundId,
            UUID leaderStudentId,
            UUID topicId,
            GroupStatus status,
            ApprovalStatus approvalStatus,
            String rejectionReason,
            List<UUID> memberStudentIds) {

        public static GroupResponse from(ThesisGroup group, List<ThesisGroupMember> members) {
            return new GroupResponse(
                    group.getId(),
                    group.getRoundId(),
                    group.getLeaderStudentId(),
                    group.getTopicId(),
                    group.getStatus(),
                    group.getApprovalStatus(),
                    group.getRejectionReason(),
                    members.stream().map(ThesisGroupMember::getStudentId).toList());
        }
    }

    public record ReviewRequest(
            @NotNull UUID councilId,
            @NotNull UUID groupId,
            @NotNull @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal score,
            @Size(max = 2000) String comment) {
    }

    public record CreateCouncilRequest(
            @NotNull UUID roundId,
            @NotNull UUID departmentId) {
    }

    public record AddCouncilMemberRequest(
            @NotNull UUID lecturerId,
            @NotNull CouncilMemberRole memberRole,
            @NotNull Integer memberOrder) {
    }

    public record ScheduleCouncilRequest(
            @NotNull Instant scheduledAt,
            @NotBlank @Size(max = 120) String room) {
    }

    public record CouncilResponse(
            UUID id,
            UUID roundId,
            UUID departmentId,
            Instant scheduledAt,
            String room,
            CouncilStatus status,
            List<CouncilMemberResponse> members) {

        public static CouncilResponse from(ThesisDefenseCouncil council, List<ThesisCouncilMember> members) {
            return new CouncilResponse(
                    council.getId(),
                    council.getRoundId(),
                    council.getDepartmentId(),
                    council.getScheduledAt(),
                    council.getRoom(),
                    council.getStatus(),
                    members.stream().map(CouncilMemberResponse::from).toList());
        }
    }

    public record CouncilMemberResponse(
            UUID lecturerId,
            CouncilMemberRole memberRole,
            int memberOrder) {

        public static CouncilMemberResponse from(ThesisCouncilMember member) {
            return new CouncilMemberResponse(
                    member.getLecturerId(),
                    member.getMemberRole(),
                    member.getMemberOrder());
        }
    }

    public record PublishResultRequest(
            @NotNull UUID councilId,
            @NotNull UUID groupId) {
    }

    public record ResultResponse(
            UUID id,
            UUID groupId,
            BigDecimal totalScore,
            String grade,
            ResultStatus status,
            Instant publishedAt) {

        public static ResultResponse from(ThesisResult result) {
            return new ResultResponse(
                    result.getId(),
                    result.getGroupId(),
                    result.getTotalScore(),
                    result.getGrade(),
                    result.getStatus(),
                    result.getPublishedAt());
        }
    }
}
