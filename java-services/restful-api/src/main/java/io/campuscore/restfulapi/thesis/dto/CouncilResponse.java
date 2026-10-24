package io.campuscore.restfulapi.thesis.dto;

import java.util.List;
import java.util.UUID;

public record CouncilResponse(
        UUID id,
        UUID roundId,
        String name,
        String status,
        List<CouncilMember> members,
        List<UUID> topicIds) { }
