package io.campuscore.restfulapi.site.dto;

/**
 * The site appearance payload as it travels between the web layer and the
 * store: raw JSON object text. The payload schema (accent, homepage hero copy,
 * homepage post order) is owned and re-validated by the frontend on every
 * read, so the backend carries it as opaque JSON rather than a typed document.
 */
public record SiteAppearancePayload(String json) {
}
