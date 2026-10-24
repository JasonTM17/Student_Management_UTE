package io.campuscore.restfulapi.thesis.dto;

public record RepositoryMemberResponse(
        String displayName,
        String studentNumber,
        boolean isExternal,
        boolean isLeader) { }
