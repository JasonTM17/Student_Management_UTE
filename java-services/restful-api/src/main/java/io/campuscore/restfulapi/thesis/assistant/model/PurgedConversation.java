package io.campuscore.restfulapi.thesis.assistant.model;

import java.util.List;
import java.util.UUID;

public record PurgedConversation(UUID id, String ownerId, List<DispatchHandle> handles) { }
