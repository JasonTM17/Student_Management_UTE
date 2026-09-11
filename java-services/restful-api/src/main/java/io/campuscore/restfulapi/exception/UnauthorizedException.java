package io.campuscore.restfulapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an unauthenticated actor attempts an action requiring credentials.
 */
public class UnauthorizedException extends AppException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED.name(), message);
    }
}
