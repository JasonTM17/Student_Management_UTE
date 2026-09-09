package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.domain.ApprovalStatus;
import io.campuscore.restfulapi.thesis.domain.GroupStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

public final class ThesisGroupReadDtos {
    private ThesisGroupReadDtos() { }

    public record GroupResponse(UUID id, UUID roundId, String leaderStudentId, UUID topicId,
            GroupStatus status, ApprovalStatus approvalStatus, String rejectionReason,
            List<String> memberStudentIds,
            List<GroupMemberResponse> members) { }

    /**
     * One group member. Internal members resolve against a student profile;
     * external members (different department or school) carry a declared
     * displayName/contact and isExternal = true.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GroupMemberResponse(
            String studentId,
            String displayName,
            String contact,
            boolean isExternal,
            boolean isLeader,
            int memberOrder) { }
}
