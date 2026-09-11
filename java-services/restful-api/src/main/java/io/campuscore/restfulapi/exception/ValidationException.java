package io.campuscore.restfulapi.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Thrown when business validation fails with field-specific errors.
 */
public class ValidationException extends AppException {

    private final Map<String, String> fieldErrors;

    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.name(), message);
        this.fieldErrors = Map.of();
    }

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.name(), message);
        this.fieldErrors = fieldErrors != null ? fieldErrors : Map.of();
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
