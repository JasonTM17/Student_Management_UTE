package io.campuscore.restfulapi.thesis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

/**
 * JPA entity mapping to thesis.thesis_group_report.
 */
@Entity
@Table(name = "thesis_group_report", schema = "thesis")
public class ThesisGroupReport {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "round_id", nullable = false)
    private UUID roundId;

    @Column(name = "submitted_by", nullable = false, length = 120)
    private String submittedBy;

    @Column(length = 240)
    private String title;

    @Column(length = 500)
    private String url;

    @Column(length = 500)
    private String note;

    @Column(name = "submitted_at", nullable = false, insertable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_type", length = 120)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_data")
    private byte[] fileData;

    @Column(name = "storage_provider", length = 32)
    private String storageProvider;

    @Column(name = "storage_bucket", length = 80)
    private String storageBucket;

    @Column(name = "storage_key", length = 512)
    private String storageKey;

    @Column(name = "file_sha256", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String fileSha256;

    protected ThesisGroupReport() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getRoundId() {
        return roundId;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getNote() {
        return note;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public byte[] getFileData() {
        return fileData;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public String getStorageBucket() {
        return storageBucket;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getFileSha256() {
        return fileSha256;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ThesisGroupReport other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
