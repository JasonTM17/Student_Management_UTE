package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.domain.CouncilMemberRole;
import io.campuscore.restfulapi.thesis.domain.CouncilStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ThesisCouncilReadDtos {
    private ThesisCouncilReadDtos() { }

    public record CouncilResponse(
            UUID id,
            UUID roundId,
            UUID departmentId,
            Instant scheduledAt,
            String room,
            CouncilStatus status,
            List<CouncilMemberResponse> members) { }

    public record CouncilMemberResponse(
            UUID lecturerId,
            CouncilMemberRole memberRole,
            int memberOrder) { }
}
