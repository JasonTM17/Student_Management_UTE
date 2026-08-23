package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.domain.ApprovalStatus;
import io.campuscore.restfulapi.thesis.domain.GroupStatus;
import java.util.List;
import java.util.UUID;

public final class ThesisGroupReadDtos {
    private ThesisGroupReadDtos() { }

    public record GroupResponse(UUID id, UUID roundId, String leaderStudentId, UUID topicId,
            GroupStatus status, ApprovalStatus approvalStatus, String rejectionReason,
            List<String> memberStudentIds) { }
}
