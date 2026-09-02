package io.campuscore.restfulapi.thesis.assistant;

import java.util.Locale;
import java.util.Set;

/** Single allowlist shared by authoring, retrieval, and citation contracts. */
public final class AssistantKnowledgeDomains {
    public static final String THESIS = "THESIS";
    public static final String REGISTRATION = "REGISTRATION";
    public static final String ACADEMIC_CATALOG = "ACADEMIC_CATALOG";
    public static final String ANNOUNCEMENT = "ANNOUNCEMENT";
    public static final String POLICY = "POLICY";
    public static final String GENERAL_FAQ = "GENERAL_FAQ";

    private static final Set<String> ALLOWED = Set.of(
            THESIS, REGISTRATION, ACADEMIC_CATALOG, ANNOUNCEMENT, POLICY, GENERAL_FAQ);

    private AssistantKnowledgeDomains() { }

    public static boolean isAllowed(String value) {
        return value != null && ALLOWED.contains(value.trim().toUpperCase(Locale.ROOT));
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return THESIS;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return ALLOWED.contains(normalized) ? normalized : THESIS;
    }
}
