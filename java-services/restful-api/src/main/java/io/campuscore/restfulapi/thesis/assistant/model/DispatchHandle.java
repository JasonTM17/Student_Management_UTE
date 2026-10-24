package io.campuscore.restfulapi.thesis.assistant.model;

import java.util.UUID;

public record DispatchHandle(UUID clientRequestId, long leaseGeneration) { }
