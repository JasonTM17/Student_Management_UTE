package io.campuscore.restfulapi.thesis.assistant.exception;

public class ProviderUnavailableException extends RuntimeException {
    private final boolean retryableBeforeContent;
    private final boolean malformed;

    public ProviderUnavailableException(String message) { this(message, false, null, false); }
    public ProviderUnavailableException(String message, Throwable cause) { this(message, false, cause, false); }
    public ProviderUnavailableException(String message, boolean retryableBeforeContent, Throwable cause, boolean malformed) {
        super(message, cause);
        this.retryableBeforeContent = retryableBeforeContent;
        this.malformed = malformed;
    }
    public boolean retryableBeforeContent() { return retryableBeforeContent; }
    public boolean malformed() { return malformed; }
}
