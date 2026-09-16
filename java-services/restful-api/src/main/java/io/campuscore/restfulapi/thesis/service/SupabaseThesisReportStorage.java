package io.campuscore.restfulapi.thesis.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.util.StringUtils;

/**
 * Server-side Supabase Storage adapter. The service-role key is read only from
 * runtime configuration and is never returned to the browser.
 */
public final class SupabaseThesisReportStorage implements ThesisReportStorage {

    private final HttpClient client;
    private final URI objectBase;
    private final String bucket;
    private final String serviceRoleKey;
    private final Duration timeout;

    public SupabaseThesisReportStorage(ThesisReportStorageProperties properties) {
        if (!StringUtils.hasText(properties.getSupabaseUrl())
                || !StringUtils.hasText(properties.getSupabaseServiceRoleKey())) {
            throw new IllegalStateException(
                    "Supabase thesis report storage requires SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY");
        }
        this.bucket = properties.getBucket();
        if (!StringUtils.hasText(bucket) || !bucket.matches("[A-Za-z0-9._-]{1,80}")) {
            throw new IllegalStateException("thesis.report.storage.bucket must be a safe bucket name");
        }
        this.serviceRoleKey = properties.getSupabaseServiceRoleKey().trim();
        this.timeout = Duration.ofMillis(Math.max(1_000, properties.getTimeoutMs()));
        String base = properties.getSupabaseUrl().trim().replaceAll("/+$", "");
        this.objectBase = URI.create(base + "/storage/v1/object/" + encodeSegment(bucket) + "/");
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public String provider() {
        return "supabase";
    }

    @Override
    public String bucket() {
        return bucket;
    }

    @Override
    public void put(String key, String contentType, byte[] data) {
        request("PUT", key, contentType, data, false);
    }

    @Override
    public byte[] read(String key) {
        return request("GET", key, null, null, true);
    }

    @Override
    public void delete(String key) {
        try {
            request("DELETE", key, null, null, false);
        } catch (ThesisReportStorageException exception) {
            // A cleanup race should not turn an already persisted report into a
            // user-facing failure. The object is immutable and keys are unique.
            if (!exception.getMessage().contains("status 404")) {
                throw exception;
            }
        }
    }

    private byte[] request(String method, String key, String contentType, byte[] body, boolean returnBody) {
        URI uri = objectBase.resolve(encodeKey(key));
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Authorization", "Bearer " + serviceRoleKey)
                .header("apikey", serviceRoleKey);
        if ("PUT".equals(method)) {
            builder.header("Content-Type", StringUtils.hasText(contentType)
                    ? contentType : "application/octet-stream");
            builder.header("x-upsert", "false");
            builder.PUT(HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body));
        } else if ("GET".equals(method)) {
            builder.GET();
        } else if ("DELETE".equals(method)) {
            builder.DELETE();
        } else {
            throw new ThesisReportStorageException("Unsupported thesis storage operation");
        }
        try {
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new ThesisReportStorageException("Supabase thesis storage returned status " + status);
            }
            return returnBody ? response.body() : new byte[0];
        } catch (IOException exception) {
            throw new ThesisReportStorageException("Supabase thesis report storage is unavailable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ThesisReportStorageException("Supabase thesis report storage request was interrupted", exception);
        }
    }

    private static String encodeKey(String key) {
        if (!StringUtils.hasText(key) || key.startsWith("/") || key.startsWith("\\")
                || key.contains("..")) {
            throw new ThesisReportStorageException("The thesis report storage key is invalid");
        }
        return Arrays.stream(key.split("/", -1))
                .map(SupabaseThesisReportStorage::encodeSegment)
                .reduce((left, right) -> left + "/" + right)
                .orElseThrow(() -> new ThesisReportStorageException("The thesis report storage key is invalid"));
    }

    private static String encodeSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
