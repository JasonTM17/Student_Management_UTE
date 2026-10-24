package io.campuscore.restfulapi.thesis.assistant.model;

import java.util.List;

public record CompletionResult(String answer, List<ProviderSegment> segments, String finishReason) { }
