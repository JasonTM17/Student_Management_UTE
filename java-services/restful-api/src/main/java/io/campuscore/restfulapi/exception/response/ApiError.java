package io.campuscore.restfulapi.exception.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Legacy error DTO preserved for backward compatibility when reading
 * upstream error payloads. Active exception handling is managed by
 * {@link io.campuscore.restfulapi.exception.handler.GlobalExceptionHandler}.
 */
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
