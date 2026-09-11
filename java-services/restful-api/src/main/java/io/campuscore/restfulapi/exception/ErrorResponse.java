package io.campuscore.restfulapi.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Standard client-facing error envelope for all CampusUTE REST API endpoints.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId,
        List<FieldErrorItem> fieldErrors,
        Map<String, String> fields) {

    public ErrorResponse(
            Instant timestamp,
            int status,
            String code,
            String message,
            String path,
            String requestId,
            Map<String, String> fields) {
        this(
                timestamp,
                status,
                code,
                message,
                path,
                requestId,
                fields == null ? List.of() : fields.entrySet().stream()
                        .map(e -> new FieldErrorItem(e.getKey(), e.getValue()))
                        .toList(),
                fields == null ? Map.of() : fields);
    }

    public record FieldErrorItem(String field, String message) {
    }
}
