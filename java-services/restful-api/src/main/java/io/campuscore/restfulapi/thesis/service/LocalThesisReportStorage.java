package io.campuscore.restfulapi.thesis.service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import org.springframework.util.StringUtils;

/**
 * Local object storage used by the Docker preview. The compose volume keeps
 * the bytes outside PostgreSQL while preserving them across API restarts.
 */
public final class LocalThesisReportStorage implements ThesisReportStorage {

    private final Path root;
    private final String bucket;

    public LocalThesisReportStorage(ThesisReportStorageProperties properties) {
        this.bucket = requireBucket(properties.getBucket());
        this.root = Paths.get(requireRoot(properties.getLocalRoot())).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new ThesisReportStorageException("Local thesis report storage is unavailable", exception);
        }
    }

    @Override
    public String provider() {
        return "local";
    }

    @Override
    public String bucket() {
        return bucket;
    }

    @Override
    public void put(String key, String contentType, byte[] data) {
        Path target = resolveKey(key);
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temporary, data, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new ThesisReportStorageException("The thesis report could not be stored locally", exception);
        }
    }

    @Override
    public byte[] read(String key) {
        try {
            return Files.readAllBytes(resolveKey(key));
        } catch (IOException exception) {
            throw new ThesisReportStorageException("The stored thesis report is unavailable", exception);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolveKey(key));
        } catch (IOException exception) {
            throw new ThesisReportStorageException("The stored thesis report could not be removed", exception);
        }
    }

    private Path resolveKey(String key) {
        if (!StringUtils.hasText(key) || key.startsWith("/") || key.startsWith("\\")) {
            throw new ThesisReportStorageException("The thesis report storage key is invalid");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new ThesisReportStorageException("The thesis report storage key is invalid");
        }
        return resolved;
    }

    private static String requireBucket(String value) {
        if (!StringUtils.hasText(value) || !value.matches("[A-Za-z0-9._-]{1,80}")) {
            throw new IllegalStateException("thesis.report.storage.bucket must be a safe bucket name");
        }
        return value.trim();
    }

    private static String requireRoot(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("thesis.report.storage.local-root is required for local storage");
        }
        return value.trim();
    }
}
