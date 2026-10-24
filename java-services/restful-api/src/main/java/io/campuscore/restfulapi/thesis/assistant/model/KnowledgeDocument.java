package io.campuscore.restfulapi.thesis.assistant.model;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeDocument(
        String id,
        String slug,
        String locale,
        String title,
        String content,
        String source,
        String domain,
        String catalogEntityType,
        String catalogEntityId,
        Instant catalogUpdatedAt,
        UUID revisionId,
        Integer revisionVersion,
        String corpusVersion,
        String corpusHash,
        UUID releaseId,
        int lexicalScore) {
    public KnowledgeDocument(String id, String slug, String locale, String title, String content, String source) {
        this(id, slug, locale, title, content, source, "THESIS", null, null, null, null, null, null, null, null, 0);
    }

    public KnowledgeDocument(String id, String slug, String locale, String title, String content, String source,
        String catalogEntityType, String catalogEntityId, Instant catalogUpdatedAt) {
        this(id, slug, locale, title, content, source, "ACADEMIC_CATALOG", catalogEntityType, catalogEntityId, catalogUpdatedAt, null, null, null, null, null, 0);
    }

    public KnowledgeDocument(String id, String slug, String locale, String title, String content, String source,
            String domain, UUID revisionId, Integer revisionVersion) {
        this(id, slug, locale, title, content, source, domain, null, null, null, revisionId, revisionVersion, null, null, null, 0);
    }

    /**
     * Pre-fast-path arity bridge: every fixture that predates the exposed
     * retrieval score binds {@code lexicalScore = 0}, which the confidence
     * gate reads as "never confident" so those documents can only ever feed
     * the provider path, never the lexical fast path.
     */
    public KnowledgeDocument(String id, String slug, String locale, String title, String content, String source,
            String domain, String catalogEntityType, String catalogEntityId, Instant catalogUpdatedAt,
            UUID revisionId, Integer revisionVersion, String corpusVersion, String corpusHash, UUID releaseId) {
        this(id, slug, locale, title, content, source, domain, catalogEntityType, catalogEntityId, catalogUpdatedAt,
                revisionId, revisionVersion, corpusVersion, corpusHash, releaseId, 0);
    }
}
