package io.campuscore.restfulapi.thesis.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "assistant.supabase")
public record SupabaseKnowledgeProperties(
        boolean enabled,
        String url,
        String serviceRoleKey,
        String schema,
        String releaseTable,
        String documentTable,
        int connectTimeoutMs,
        int readTimeoutMs) {

    @ConstructorBinding
    public SupabaseKnowledgeProperties(boolean enabled, String url, String serviceRoleKey, String schema,
            String releaseTable, String documentTable, int connectTimeoutMs, int readTimeoutMs) {
        this.enabled = enabled;
        this.url = trim(url);
        this.serviceRoleKey = trim(serviceRoleKey);
        this.schema = schema == null || schema.isBlank() ? "assistant" : schema.trim();
        this.releaseTable = tableName(releaseTable, "knowledge_release");
        this.documentTable = tableName(documentTable, "knowledge_release_document");
        this.connectTimeoutMs = clamp(connectTimeoutMs, 250, 10_000);
        this.readTimeoutMs = clamp(readTimeoutMs, 1_000, 120_000);
    }

    public boolean usable() {
        if (!enabled || url.isBlank() || serviceRoleKey.isBlank()) return false;
        try {
            java.net.URI parsed = java.net.URI.create(url);
            return "https".equalsIgnoreCase(parsed.getScheme())
                    && parsed.getHost() != null
                    && parsed.getUserInfo() == null
                    && parsed.getQuery() == null
                    && parsed.getFragment() == null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }

    private static String tableName(String value, String fallback) {
        String candidate = value == null || value.isBlank() ? fallback : value.trim();
        if (!candidate.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalArgumentException("Supabase table name is invalid");
        }
        return candidate;
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
