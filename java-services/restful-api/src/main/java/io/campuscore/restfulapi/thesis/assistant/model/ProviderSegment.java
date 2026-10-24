package io.campuscore.restfulapi.thesis.assistant.model;

import java.util.List;

public record ProviderSegment(int sequence, String text, List<String> sourceIds) { }
