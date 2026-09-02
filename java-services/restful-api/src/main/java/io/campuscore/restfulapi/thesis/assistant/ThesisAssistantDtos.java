package io.campuscore.restfulapi.thesis.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class ThesisAssistantDtos {

    private ThesisAssistantDtos() {
    }

    public record ChatRequest(
            @NotBlank(message = "message is required")
            @Size(max = 2000, message = "message must contain at most 2000 characters")
            String message,
            @NotBlank(message = "locale is required")
            @Pattern(regexp = "^(en|vi)$", message = "locale must be en or vi")
            String locale,
            @NotNull(message = "clientRequestId is required")
            UUID clientRequestId,
            String conversationId) {
        public ChatRequest(String message, String locale) {
            this(message, locale, UUID.randomUUID(), null);
        }

        public ChatRequest(String message, String locale, String conversationId) {
            this(message, locale, UUID.randomUUID(), conversationId);
        }
    }

    public record Citation(
            String id,
            String slug,
            String title,
            String source,
            String locale,
            String excerpt,
            String domain,
            String sourceKind,
            String sourceId,
            UUID revisionId,
            Integer revisionVersion,
            String snapshotHash,
            String entityType,
            String entityId,
            String updatedAt,
            String corpusVersion,
            String corpusHash,
            UUID releaseId) {
        public Citation(String id, String slug, String title, String source, String locale, String excerpt) {
            this(id, slug, title, source, locale, excerpt, "THESIS", "CURATED", id,
                    parseUuid(id), null, null, null, null, null, null, null, null);
        }

        /** Backwards-compatible constructor for persisted rows from V12-V15. */
        public Citation(String id, String slug, String title, String source, String locale, String excerpt,
                String domain, String sourceKind, String sourceId, UUID revisionId, Integer revisionVersion,
                String snapshotHash, String entityType, String entityId, String updatedAt) {
            this(id, slug, title, source, locale, excerpt, domain, sourceKind, sourceId, revisionId,
                    revisionVersion, snapshotHash, entityType, entityId, updatedAt, null, null, null);
        }
    }

    public record ChatResponse(
            String answer,
            String model,
            boolean degraded,
            String reasonCode,
            String locale,
            List<Citation> citations,
            UUID requestId,
            UUID clientRequestId,
            UUID turnId,
            boolean replayed,
            String terminalStatus,
            String conversationId,
            String messageId) {
        public ChatResponse(String answer, String model, boolean degraded, String reasonCode,
                String locale, List<Citation> citations) {
            this(answer, model, degraded, reasonCode, locale, citations, null, null, null, false, null, null, null);
        }

        public ChatResponse(String answer, String model, boolean degraded, String reasonCode,
                String locale, List<Citation> citations, String conversationId, String messageId) {
            this(answer, model, degraded, reasonCode, locale, citations, null, null, null, false, null,
                    conversationId, messageId);
        }
    }

    public record FeedbackRequest(
            @NotBlank(message = "rating is required")
            @Pattern(regexp = "^(UP|DOWN)$", message = "rating must be UP or DOWN")
            String rating,
            @Pattern(regexp = "^(HELPFUL|CLEAR|INCORRECT|OUTDATED|NOT_RELEVANT|UNSAFE)$",
                    message = "reason is not supported")
            String reason) { }

    public record FeedbackResponse(UUID messageId, String rating, String reason, boolean removed) { }

    public record CursorPage<T>(List<T> data, String nextCursor) { }

    private static UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
