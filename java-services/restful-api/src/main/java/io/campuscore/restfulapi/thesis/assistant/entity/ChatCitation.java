package io.campuscore.restfulapi.thesis.assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping to assistant.chat_citation.
 */
@Entity
@Table(name = "chat_citation", schema = "assistant")
public class ChatCitation {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(nullable = false, length = 180)
    private String slug;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String source;

    @Column(nullable = false, length = 8)
    private String locale;

    @Column(nullable = false, columnDefinition = "text")
    private String excerpt;

    @Column(nullable = false, length = 48, insertable = false, updatable = false)
    private String domain;

    @Column(name = "source_kind", nullable = false, length = 24, insertable = false, updatable = false)
    private String sourceKind;

    @Column(name = "source_id", length = 180)
    private String sourceId;

    @Column(name = "revision_id")
    private UUID revisionId;

    @Column(name = "revision_version")
    private Integer revisionVersion;

    @Column(name = "catalog_entity_type", length = 48)
    private String catalogEntityType;

    @Column(name = "catalog_entity_id", length = 180)
    private String catalogEntityId;

    @Column(name = "catalog_updated_at")
    private Instant catalogUpdatedAt;

    @Column(name = "snapshot_hash", length = 64)
    private String snapshotHash;

    @Column(nullable = false, insertable = false, updatable = false)
    private int ordinal;

    @Column(name = "corpus_version", length = 120)
    private String corpusVersion;

    @Column(name = "corpus_hash", length = 64)
    private String corpusHash;

    @Column(name = "release_id")
    private UUID releaseId;

    protected ChatCitation() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getSource() {
        return source;
    }

    public String getLocale() {
        return locale;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public String getDomain() {
        return domain;
    }

    public String getSourceKind() {
        return sourceKind;
    }

    public String getSourceId() {
        return sourceId;
    }

    public UUID getRevisionId() {
        return revisionId;
    }

    public Integer getRevisionVersion() {
        return revisionVersion;
    }

    public String getCatalogEntityType() {
        return catalogEntityType;
    }

    public String getCatalogEntityId() {
        return catalogEntityId;
    }

    public Instant getCatalogUpdatedAt() {
        return catalogUpdatedAt;
    }

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public String getCorpusVersion() {
        return corpusVersion;
    }

    public String getCorpusHash() {
        return corpusHash;
    }

    public UUID getReleaseId() {
        return releaseId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatCitation other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
