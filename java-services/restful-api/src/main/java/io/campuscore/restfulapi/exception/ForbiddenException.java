package io.campuscore.restfulapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated actor lacks permission for an operation.
 */
public class ForbiddenException extends AppException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.name(), message);
    }

    public ForbiddenException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
}
