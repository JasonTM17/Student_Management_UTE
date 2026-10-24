package io.campuscore.restfulapi.thesis.assistant.dto;

import java.util.UUID;

public record KnowledgeRevision(UUID documentId, UUID revisionId, int version, String state,
        UUID releaseId, String corpusVersion, String corpusHash, Integer rowCount,
        SyncResult sync) {
    public KnowledgeRevision(UUID documentId, UUID revisionId, int version, String state) {
        this(documentId, revisionId, version, state, null, null, null, null, null);
    }
}
