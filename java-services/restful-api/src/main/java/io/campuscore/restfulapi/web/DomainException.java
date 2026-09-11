package io.campuscore.restfulapi.web;

import io.campuscore.restfulapi.exception.AppException;
import org.springframework.http.HttpStatus;

/** A safe, client-facing domain failure with a stable error code. */
public class DomainException extends AppException {

    public DomainException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
