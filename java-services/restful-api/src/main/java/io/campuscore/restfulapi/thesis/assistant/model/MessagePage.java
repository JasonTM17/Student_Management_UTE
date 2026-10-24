package io.campuscore.restfulapi.thesis.assistant.model;

import java.util.List;

public record MessagePage(List<Message> data, String nextCursor) { }
