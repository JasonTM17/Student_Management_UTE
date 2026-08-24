package io.campuscore.restfulapi.registration;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Stable registration rejection; the web advice maps this to application/problem+json. */
public final class RegistrationProblemException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final boolean retryable;
    private final List<Violation> violations;

    public RegistrationProblemException(HttpStatus status, String code, String message) {
        this(status, code, message, false, List.of());
    }

    public RegistrationProblemException(HttpStatus status, String code, String message,
            boolean retryable, List<Violation> violations) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public boolean retryable() { return retryable; }
    public List<Violation> violations() { return violations; }

    public record Violation(String sectionId, String conflictsWithSectionId, String message) { }
}
