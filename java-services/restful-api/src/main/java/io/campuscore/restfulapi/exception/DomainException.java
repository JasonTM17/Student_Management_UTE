package io.campuscore.restfulapi.exception;

import io.campuscore.restfulapi.exception.AppException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** A safe, client-facing domain failure with a stable error code. */
public class DomainException extends AppException {

    private final Map<String, Object> details;

    public DomainException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public DomainException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(status, code, message);
        this.details = details;
    }

    /**
     * Optional machine-readable detail for callers that can act on the
     * failure (for example an admin reopen conflict list). Callers only ever
     * see this payload when the endpoint itself was authorized to raise it.
     */
    public Map<String, Object> getDetails() {
        return details;
    }
}
