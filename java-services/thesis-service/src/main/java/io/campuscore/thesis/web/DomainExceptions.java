package io.campuscore.thesis.web;

public final class DomainExceptions {

    private DomainExceptions() {
    }

    public static class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }

    public static class Conflict extends RuntimeException {
        public Conflict(String message) {
            super(message);
        }
    }

    public static class InvalidState extends RuntimeException {
        public InvalidState(String message) {
            super(message);
        }
    }

    public static class RateLimited extends RuntimeException {
        public RateLimited(String message) {
            super(message);
        }
    }

    public static class ServiceUnavailable extends RuntimeException {
        public ServiceUnavailable(String message) {
            super(message);
        }
    }
}
