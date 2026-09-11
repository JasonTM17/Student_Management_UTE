package io.campuscore.restfulapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Base application runtime exception with structured ErrorCode.
 */
public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.status = errorCode.getHttpStatus();
        this.code = errorCode.name();
    }

    public AppException(ErrorCode errorCode, String customMessage) {
        super(customMessage != null && !customMessage.isBlank() ? customMessage : errorCode.getDefaultMessage());
        this.status = errorCode.getHttpStatus();
        this.code = errorCode.name();
    }

    public AppException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public HttpStatus status() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String code() {
        return code;
    }
}
