package io.campuscore.restfulapi.thesis.assistant.dto;

public record KnowledgeRequest(String slug, String locale, String title, String content, String source, Integer priority, String domain) {
    public KnowledgeRequest {
        domain = domain == null || domain.isBlank() ? "THESIS" : domain.trim().toUpperCase(java.util.Locale.ROOT);
    }
    public KnowledgeRequest(String slug, String locale, String title, String content, String source, Integer priority) {
        this(slug, locale, title, content, source, priority, "THESIS");
    }
}
