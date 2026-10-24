package io.campuscore.restfulapi.thesis.dto;

import io.campuscore.restfulapi.thesis.model.ApprovalStatus;
import io.campuscore.restfulapi.thesis.model.GroupStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

public final class ThesisGroupReadDtos {
    private ThesisGroupReadDtos() { }

    public record GroupResponse(UUID id, UUID roundId, String leaderStudentId, UUID topicId,
            GroupStatus status, ApprovalStatus approvalStatus, String rejectionReason,
            List<String> memberStudentIds,
            List<GroupMemberResponse> members,
            /**
             * Withdraw-approval affordance for the requesting actor, computed
             * like the round deletion guard: {@code withdrawable} only when the
             * actor may withdraw now (approved state, live round, no downstream
             * work, in scope); the counts name the blocker kinds so an admin
             * knows to reach for the roster override instead.
             */
            GroupWithdrawalInfo approvalWithdrawal) {

        public GroupResponse withApprovalWithdrawal(GroupWithdrawalInfo info) {
            return new GroupResponse(id, roundId, leaderStudentId, topicId, status,
                    approvalStatus, rejectionReason, memberStudentIds, members, info);
        }
    }

    /**
     * Per-kind downstream-work counts behind {@code withdrawable}: group
     * reports, topic scores, council-topic assignments, reviewer assignment
     * (0/1) and topic results (0/1).
     */
    public record GroupWithdrawalInfo(
            boolean withdrawable,
            long reports,
            long scores,
            long councilTopics,
            long reviewers,
            long results) { }

    /**
     * One group member. studentId is the internal profile id used by mutation
     * endpoints; studentNumber is the campus-facing student code. Internal
     * members resolve displayName/contact from the student profile, while
     * external members carry a declared displayName/contact and isExternal =
     * true.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GroupMemberResponse(
            String studentId,
            String studentNumber,
            String displayName,
            String contact,
            boolean isExternal,
            boolean isLeader,
            int memberOrder) { }
}
