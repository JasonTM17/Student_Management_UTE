package io.campuscore.restfulapi.thesis.assistant.exception;

public final class ProviderCancelledException extends RuntimeException {
    public ProviderCancelledException() { super("assistant request cancelled"); }
}
