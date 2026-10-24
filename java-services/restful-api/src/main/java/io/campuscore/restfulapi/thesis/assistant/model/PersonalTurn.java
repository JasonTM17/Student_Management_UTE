package io.campuscore.restfulapi.thesis.assistant.model;

/**
 * Result of {@link #recordPersonalTurn}: the conversation the turn landed
 * in plus the persisted assistant message id so responses can stamp both —
 * the stream's done frame needs messageId for feedback to bind.
 */
public record PersonalTurn(String conversationId, String messageId) { }
