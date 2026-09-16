package io.campuscore.restfulapi.thesis.service;

/**
 * Provider-neutral object storage boundary for thesis report artifacts.
 * PostgreSQL keeps the report metadata and authorization; the document bytes
 * live behind this boundary so local preview and Supabase Storage use the same
 * API contract.
 */
public interface ThesisReportStorage {

    String provider();

    String bucket();

    void put(String key, String contentType, byte[] data);

    byte[] read(String key);

    void delete(String key);
}
