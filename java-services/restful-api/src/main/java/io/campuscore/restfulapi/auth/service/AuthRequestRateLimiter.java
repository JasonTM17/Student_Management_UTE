package io.campuscore.restfulapi.auth.service;

import io.campuscore.restfulapi.auth.repository.AuthRateLimitRepository;
import io.campuscore.restfulapi.web.DomainException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Database-backed limiter that stores only SHA-256 email and IP identities. */
@Component
@Profile("persistence")
public final class AuthRequestRateLimiter {

    private final AuthRateLimitRepository buckets;
    private final Clock clock;
    private final int maximumPerDay;

    @Autowired
    public AuthRequestRateLimiter(
            AuthRateLimitRepository buckets,
            @Value("${auth.lifecycle.max-requests-per-day:5}") int maximumPerDay) {
        this(buckets, Clock.systemUTC(), maximumPerDay);
    }

    AuthRequestRateLimiter(AuthRateLimitRepository buckets, Clock clock, int maximumPerDay) {
        if (maximumPerDay <= 0) {
            throw new IllegalArgumentException("Auth rate-limit maximum must be positive");
        }
        this.buckets = buckets;
        this.clock = clock;
        this.maximumPerDay = maximumPerDay;
    }

    public void check(String operation, String normalizedEmail, String ipAddress) {
        Instant now = clock.instant();
        long day = Math.floorDiv(now.getEpochSecond(), 86_400L);
        Instant windowStart = Instant.ofEpochSecond(day * 86_400L);
        boolean emailAccepted = buckets.consume(
                operation + ":EMAIL",
                AuthChallengeTokenService.sha256(normalizedEmail),
                windowStart,
                maximumPerDay,
                now);
        boolean ipAccepted = buckets.consume(
                operation + ":IP",
                AuthChallengeTokenService.sha256(ipAddress == null ? "unknown" : ipAddress),
                windowStart,
                maximumPerDay,
                now);
        if (!emailAccepted || !ipAccepted) {
            throw new DomainException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "AUTH_RATE_LIMITED",
                    "Too many authentication requests; try again later");
        }
    }
}
