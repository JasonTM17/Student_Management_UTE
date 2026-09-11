package io.campuscore.restfulapi.security.ratelimit;

/**
 * Value object holding the outcome of a rate limit check.
 */
public record RateLimitResult(
        boolean allowed,
        int limit,
        long remaining,
        long resetEpochSeconds,
        long retryAfterSeconds) {

    public static RateLimitResult allowed(int limit, long remaining, long resetEpochSeconds) {
        return new RateLimitResult(true, limit, Math.max(0, remaining), resetEpochSeconds, 0);
    }

    public static RateLimitResult blocked(int limit, long resetEpochSeconds, long retryAfterSeconds) {
        return new RateLimitResult(false, limit, 0, resetEpochSeconds, Math.max(1, retryAfterSeconds));
    }
}
