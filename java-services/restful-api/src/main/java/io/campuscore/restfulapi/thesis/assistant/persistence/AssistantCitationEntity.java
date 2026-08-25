package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Immutable source provenance attached to an assistant message. */
@Entity
@Table(name = "chat_citation", schema = "assistant")
public class AssistantCitationEntity {
    @Id private UUID id;
    @Column(name = "message_id", nullable = false) private UUID messageId;
    @Column(name = "document_id") private UUID documentId;
    @Column(name = "slug", nullable = false, length = 180) private String slug;
    @Column(name = "title", nullable = false) private String title;
    @Column(name = "source", nullable = false) private String source;
    @Column(name = "locale", nullable = false, length = 8) private String locale;
    @Column(name = "excerpt", nullable = false) private String excerpt;
    @Column(name = "domain", nullable = false, length = 48) private String domain;
    @Column(name = "source_kind", nullable = false, length = 24) private String sourceKind;
    @Column(name = "source_id", length = 180) private String sourceId;
    @Column(name = "revision_id") private UUID revisionId;
    @Column(name = "revision_version") private Integer revisionVersion;
    @Column(name = "catalog_entity_type", length = 48) private String catalogEntityType;
    @Column(name = "catalog_entity_id", length = 180) private String catalogEntityId;
    @Column(name = "catalog_updated_at") private Instant catalogUpdatedAt;
    @Column(name = "snapshot_hash", length = 64) private String snapshotHash;
    @Column(name = "ordinal", nullable = false) private int ordinal;

    protected AssistantCitationEntity() { }

    public UUID getId() { return id; }
    public UUID getMessageId() { return messageId; }
    public UUID getDocumentId() { return documentId; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getSource() { return source; }
    public String getLocale() { return locale; }
    public String getExcerpt() { return excerpt; }
    public String getDomain() { return domain; }
    public String getSourceKind() { return sourceKind; }
    public String getSourceId() { return sourceId; }
    public UUID getRevisionId() { return revisionId; }
    public Integer getRevisionVersion() { return revisionVersion; }
    public String getCatalogEntityType() { return catalogEntityType; }
    public String getCatalogEntityId() { return catalogEntityId; }
    public Instant getCatalogUpdatedAt() { return catalogUpdatedAt; }
    public String getSnapshotHash() { return snapshotHash; }
    public int getOrdinal() { return ordinal; }
}
