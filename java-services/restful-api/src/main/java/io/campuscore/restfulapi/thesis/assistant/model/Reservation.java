package io.campuscore.restfulapi.thesis.assistant.model;

import java.util.UUID;

public record Reservation(ReservationStatus status, UUID turnId, UUID conversationId, long leaseGeneration, boolean createdConversation, UUID resultMessageId, String terminalState, boolean replayed) { }
