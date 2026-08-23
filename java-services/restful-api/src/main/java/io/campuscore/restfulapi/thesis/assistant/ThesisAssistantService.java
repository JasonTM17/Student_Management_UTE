package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.Citation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** Deterministic lexical RAG over the curated PostgreSQL thesis corpus. */
@Service
@Profile("persistence")
public class ThesisAssistantService {

    static final String MODEL = "curated-lexical-rag";
    static final int TOP_K = 3;
    private static final String DEFAULT_LOCALE = "vi";

    private final ThesisAssistantKnowledgeRepository knowledge;

    public ThesisAssistantService(ThesisAssistantKnowledgeRepository knowledge) {
        this.knowledge = knowledge;
    }

    public ChatResponse answer(String message, String locale) {
        String normalizedMessage = message == null ? "" : message.trim();
        if (normalizedMessage.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        if (normalizedMessage.length() > 2000) {
            throw new IllegalArgumentException("message must contain at most 2000 characters");
        }

        String requestedLocale = normalizeLocale(locale);
        List<String> terms = tokenize(normalizedMessage);
        List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> documents;
        try {
            documents = retrieve(requestedLocale, terms);
        } catch (DataAccessException exception) {
            return new ChatResponse(
                    unavailableMessage(requestedLocale), MODEL, true,
                    "KNOWLEDGE_UNAVAILABLE", requestedLocale, List.of());
        }

        if (documents.isEmpty()) {
            return new ChatResponse(
                    noMatchMessage(requestedLocale), MODEL, false,
                    "NO_MATCH", requestedLocale, List.of());
        }

        List<Citation> citations = documents.stream().map(ThesisAssistantService::citation).toList();
        return new ChatResponse(
                documents.get(0).content(), MODEL, false,
                "ANSWERED", requestedLocale, citations);
    }

    private List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> retrieve(
            String requestedLocale,
            List<String> terms) {
        List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> documents = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addDocuments(documents, seen, knowledge.search(requestedLocale, terms, TOP_K));
        if (documents.size() < TOP_K && !DEFAULT_LOCALE.equals(requestedLocale)) {
            addDocuments(documents, seen,
                    knowledge.search(DEFAULT_LOCALE, terms, TOP_K - documents.size()));
        }
        return documents.stream()
                .filter(document -> containsAnyTerm(document, terms))
                .limit(TOP_K)
                .toList();
    }

    private static void addDocuments(
            List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> target,
            Set<String> seen,
            List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> candidates) {
        for (ThesisAssistantKnowledgeRepository.KnowledgeDocument candidate : candidates) {
            if (seen.add(candidate.slug())) {
                target.add(candidate);
            }
        }
    }

    private static Citation citation(ThesisAssistantKnowledgeRepository.KnowledgeDocument document) {
        return new Citation(
                document.id(),
                document.slug(),
                document.title(),
                document.source(),
                document.locale(),
                excerpt(document.content()));
    }

    private static boolean containsAnyTerm(
            ThesisAssistantKnowledgeRepository.KnowledgeDocument document,
            List<String> terms) {
        String searchable = (document.title() + " " + document.content()).toLowerCase(Locale.ROOT);
        return terms.stream().anyMatch(searchable::contains);
    }

    private static String excerpt(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 280 ? normalized : normalized.substring(0, 277) + "...";
    }

    private static List<String> tokenize(String message) {
        return List.of(message.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .stream()
                .filter(term -> term.length() >= 2)
                .filter(term -> !Set.of(
                        "the", "and", "for", "how", "what", "are", "you",
                        "is", "in", "to", "of", "a", "an", "on", "my", "do",
                        "có", "cho", "và", "là", "em", "nên").contains(term))
                .distinct()
                .toList();
    }

    private static String normalizeLocale(String locale) {
        return "en".equalsIgnoreCase(locale) ? "en" : DEFAULT_LOCALE;
    }

    private static String noMatchMessage(String locale) {
        return "vi".equals(locale)
                ? "Chưa tìm thấy tài liệu phù hợp trong kho kiến thức luận văn. Hãy hỏi về chọn đề tài, đăng ký nhóm hoặc tiến độ."
                : "No matching thesis guidance was found in the curated knowledge base. Try asking about topics, groups, or progress.";
    }

    private static String unavailableMessage(String locale) {
        return "vi".equals(locale)
                ? "Kho kiến thức luận văn hiện chưa khả dụng. Vui lòng thử lại sau."
                : "The thesis knowledge base is currently unavailable. Please try again later.";
    }
}
