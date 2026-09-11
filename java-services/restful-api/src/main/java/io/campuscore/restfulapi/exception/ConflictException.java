package io.campuscore.restfulapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation conflicts with existing server or domain state.
 */
public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.CONFLICT.name(), message);
    }

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
