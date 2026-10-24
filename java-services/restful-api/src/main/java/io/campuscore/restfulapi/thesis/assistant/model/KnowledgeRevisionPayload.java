package io.campuscore.restfulapi.thesis.assistant.model;

/** Full content payload of a pending knowledge revision being published. */
public record KnowledgeRevisionPayload(String slug, String locale, String title, String content, String source, int priority, String domain) {
    public KnowledgeRevisionPayload(String slug, String locale, String title, String content, String source, int priority) {
        this(slug, locale, title, content, source, priority, "THESIS");
    }
}
