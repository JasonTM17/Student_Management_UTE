package io.campuscore.restfulapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an input argument or request is invalid.
 */
public class BadRequestException extends AppException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.name(), message);
    }

    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
