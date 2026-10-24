package io.campuscore.restfulapi.thesis.assistant.model;

import java.util.UUID;

public record ExpiredConversation(UUID id, String ownerId) { }
