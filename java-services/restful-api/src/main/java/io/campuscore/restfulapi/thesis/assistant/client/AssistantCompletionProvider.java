package io.campuscore.restfulapi.thesis.assistant.client;

import io.campuscore.restfulapi.thesis.assistant.model.CompletionRequest;
import io.campuscore.restfulapi.thesis.assistant.model.CompletionResult;
import io.campuscore.restfulapi.thesis.assistant.model.ProviderSegment;
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
}
