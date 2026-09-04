package io.campuscore.restfulapi.thesis.assistant;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Deterministically decides whether a grounded answer needs model synthesis.
 * Short single-intent lookups stay on lexical RAG; DeepSeek is reserved for
 * questions that require comparison, explanation, multiple steps, or several
 * independent knowledge documents.
 */
final class AssistantDifficultyRouter {
    private static final int LONG_QUESTION_CHARS = 180;
    private static final int MANY_TERMS = 12;
    private static final List<String> COMPLEX_MARKERS = List.of(
            "so sanh", "khac nhau", "tai sao", "vi sao", "nhu the nao", "lam the nao",
            "huong dan", "cac buoc", "ngoai le", "truong hop", "neu ", "compare",
            "difference", "why", "how", "steps", "exception", "case");

    private AssistantDifficultyRouter() {
    }

    /**
     * Returns true only when a non-empty RAG result needs synthesis beyond a
     * direct fact lookup. No network or model call occurs in this decision.
     */
    static boolean requiresSynthesis(String message,
            List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> documents) {
        if (documents == null || documents.isEmpty() || message == null) return false;
        String normalized = Normalizer.normalize(message, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        if (normalized.length() > LONG_QUESTION_CHARS
                || normalized.split("\\s+").length >= MANY_TERMS) return true;
        if (COMPLEX_MARKERS.stream().anyMatch(normalized::contains)) return true;
        return documents.size() >= 3
                || documents.stream().map(ThesisAssistantKnowledgeRepository.KnowledgeDocument::domain)
                        .filter(domain -> domain != null && !domain.isBlank()).distinct().count() >= 2;
    }
}
