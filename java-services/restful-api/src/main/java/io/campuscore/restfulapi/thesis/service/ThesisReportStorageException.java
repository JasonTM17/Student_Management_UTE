package io.campuscore.restfulapi.thesis.service;

/** Safe, provider-neutral error for report object storage failures. */
public final class ThesisReportStorageException extends RuntimeException {

    public ThesisReportStorageException(String message) {
        super(message);
    }

    public ThesisReportStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
