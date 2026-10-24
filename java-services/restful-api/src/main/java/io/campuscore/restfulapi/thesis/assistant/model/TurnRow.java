package io.campuscore.restfulapi.thesis.assistant.model;

import java.util.UUID;

public record TurnRow(UUID turnId, String ownerId, UUID clientRequestId, String requestHash, UUID conversationId, boolean createdConversation, String state, long leaseGeneration, boolean quotaReserved, UUID resultMessageId) { }
