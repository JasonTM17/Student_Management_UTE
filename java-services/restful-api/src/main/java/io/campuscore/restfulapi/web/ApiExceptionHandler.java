package io.campuscore.restfulapi.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Legacy exception DTO container preserved for backward compatibility.
 * Active exception handling is managed by {@link io.campuscore.restfulapi.exception.GlobalExceptionHandler}.
 */
public final class ApiExceptionHandler {

    private ApiExceptionHandler() {
    }

    public record ApiError(
            Instant timestamp,
            int status,
            String code,
            String message,
            String path,
            String requestId,
            List<FieldError> fieldErrors,
            Map<String, String> fields) {
    }

    public record FieldError(String field, String message) {
    }
}
