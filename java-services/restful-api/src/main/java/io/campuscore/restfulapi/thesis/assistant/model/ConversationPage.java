package io.campuscore.restfulapi.thesis.assistant.model;

import java.util.List;

public record ConversationPage(List<Conversation> data, String nextCursor) { }
