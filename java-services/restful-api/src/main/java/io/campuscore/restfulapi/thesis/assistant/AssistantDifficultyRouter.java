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
    private static final int LONG_QUESTION_CHARS = 110;
    private static final int MANY_TERMS = 8;
    private static final List<String> COMPLEX_MARKERS = List.of(
            "so sanh", "khac nhau", "tai sao", "vi sao", "nhu the nao", "lam the nao",
            "huong dan", "cac buoc", "ngoai le", "truong hop", "neu ", "phan biet",
            "dieu kien", "quy che", "quy dinh", "chi tiet", "tong hop", "phan tich",
            "giai thich", "co duoc", "co the", "khi nao", "the nao", "yeu cau",
            "thu tuc", "phuong thuc", "canh bao", "muc 1", "muc 2", "hoc bong",
            "tot nghiep", "tu van", "dieu kien gi", "can nhung gi",
            "compare", "difference", "why", "how", "steps", "exception", "case",
            "explain", "analyze", "distinguish");

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
        // Credit-cap questions have one deterministic, curated answer. Do not
        // spend a provider dispatch or invite paraphrase drift just because a
        // Vietnamese sentence contains several stop words.
        if (ThesisAssistantService.isCreditLimitQuery(normalized)) return false;
        if (normalized.length() > LONG_QUESTION_CHARS
                || normalized.split("\\s+").length >= MANY_TERMS) return true;
        if (containsComplexMarker(normalized)) return true;
        return documents.size() >= 3
                || documents.stream().map(ThesisAssistantKnowledgeRepository.KnowledgeDocument::domain)
                        .filter(domain -> domain != null && !domain.isBlank()).distinct().count() >= 2;
    }

    /**
     * Markers match on folded word boundaries: a substring contains() used to
     * fire "how" inside "show" or "case" inside "showcase", and every false
     * positive burns one of the user's twenty daily provider dispatches.
     */
    private static final List<java.util.regex.Pattern> COMPLEX_MARKER_PATTERNS = COMPLEX_MARKERS.stream()
            .map(marker -> java.util.regex.Pattern.compile(
                    "\\b" + java.util.regex.Pattern.quote(marker.trim()) + "\\b"))
            .toList();

    private static boolean containsComplexMarker(String normalized) {
        return COMPLEX_MARKER_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }
}
