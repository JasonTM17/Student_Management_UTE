package io.campuscore.restfulapi.thesis.storage;
import io.campuscore.restfulapi.thesis.exception.ThesisReportStorageException;
import io.campuscore.restfulapi.thesis.config.ThesisReportStorageProperties;


/**
 * Database-backed report storage marker. With this provider the document bytes
 * live in {@code thesis.thesis_group_report.file_data} and no {@code storage_key}
 * is written, so object operations are unreachable by design — the read path
 * ({@code storedReportFromRow}) serves {@code file_data} directly. The bucket
 * value is recorded on the row for provenance only.
 *
 * <p>This provider exists for deployments without durable object storage (e.g.
 * containers with ephemeral filesystems) where the relational database is the
 * only durable store. Files are capped at 20 MB by {@link ReportFilePolicy},
 * which keeps the column size bounded.
 */
public final class DatabaseThesisReportStorage implements ThesisReportStorage {

    private final String bucket;

    public DatabaseThesisReportStorage(ThesisReportStorageProperties properties) {
        this.bucket = properties.getBucket();
    }

    @Override
    public String provider() {
        return "database";
    }

    @Override
    public String bucket() {
        return bucket;
    }

    @Override
    public void put(String key, String contentType, byte[] data) {
        // Bytes are written into the row's file_data column by the service;
        // nothing else is required here.
    }

    @Override
    public byte[] read(String key) {
        throw new ThesisReportStorageException(
                "database provider serves report bytes from the row, not object storage");
    }

    @Override
    public void delete(String key) {
        // Nothing to delete: no object is ever stored for this provider.
    }
}
