package io.campuscore.restfulapi.thesis.assistant;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Provider seam shared by JSON collection and web SSE orchestration. */
public interface AssistantCompletionProvider {

    CompletionResult complete(CompletionRequest request, Consumer<ProviderSegment> segmentSink);

    /**
     * Cancellation-aware provider entry point. Implementations must abort any
     * outstanding transport/read handle when the supplier becomes true. The
     * default keeps small deterministic test providers source-compatible.
     */
    default CompletionResult complete(CompletionRequest request, Consumer<ProviderSegment> segmentSink,
            BooleanSupplier cancelled) {
        if (cancelled != null && cancelled.getAsBoolean()) {
            throw new java.util.concurrent.CancellationException("assistant request cancelled");
        }
        return complete(request, segmentSink);
    }

    /**
     * {@code systemPromptOverride} lets a caller swap the academic RAG prompt
     * (e.g. the general-knowledge fallback for off-topic questions); {@code
     * null} keeps the standard academic system prompt.
     */
    record CompletionRequest(String question, String locale, String context, List<String> sourceIds,
            String systemPromptOverride) {
        public CompletionRequest(String question, String locale, String context, List<String> sourceIds) {
            this(question, locale, context, sourceIds, null);
        }
    }

    record ProviderSegment(int sequence, String text, List<String> sourceIds) { }

    record CompletionResult(String answer, List<ProviderSegment> segments, String finishReason) { }
}
