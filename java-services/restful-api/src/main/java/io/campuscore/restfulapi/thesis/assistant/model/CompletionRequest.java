package io.campuscore.restfulapi.thesis.assistant.model;

import java.util.List;

/**
 * {@code systemPromptOverride} lets a caller swap the academic RAG prompt
 * (e.g. the general-knowledge fallback for off-topic questions); {@code
 * null} keeps the standard academic system prompt.
 */
public record CompletionRequest(String question, String locale, String context, List<String> sourceIds,
        String systemPromptOverride) {
    public CompletionRequest(String question, String locale, String context, List<String> sourceIds) {
        this(question, locale, context, sourceIds, null);
    }
}
