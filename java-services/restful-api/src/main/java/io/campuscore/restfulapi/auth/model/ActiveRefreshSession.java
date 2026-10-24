package io.campuscore.restfulapi.auth.model;

public record ActiveRefreshSession(AuthUserRecord user, String sessionId) {
}
