package io.campuscore.restfulapi.thesis.assistant.model;

import java.util.UUID;

public record ExpiredLease(String ownerId, UUID clientRequestId, UUID turnId, long leaseGeneration, String terminalState) { }
