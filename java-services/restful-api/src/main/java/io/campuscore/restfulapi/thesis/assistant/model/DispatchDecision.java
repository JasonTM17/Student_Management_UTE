package io.campuscore.restfulapi.thesis.assistant.model;

public record DispatchDecision(boolean dispatched, boolean quotaReserved, String reasonCode) { }
