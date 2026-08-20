package io.campuscore.restfulapi.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues the Java monolith access token shape without moving auth ownership yet. */
@Service
public class AuthTokenService {

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration accessTokenTtl;

    @Autowired
    public AuthTokenService(
            JwtEncoder jwtEncoder,
            @Value("${security.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds) {
        this(jwtEncoder, Clock.systemUTC(), Duration.ofSeconds(accessTokenTtlSeconds));
    }

    AuthTokenService(JwtEncoder jwtEncoder, Clock clock, Duration accessTokenTtl) {
        if (accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("Access token TTL must be positive");
        }
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.accessTokenTtl = accessTokenTtl;
    }

    public IssuedAccessToken issueAccessToken(AuthPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        requireText(principal.id(), "subject");
        requireText(principal.email(), "email");
        List<String> roles = values(principal.roles(), "roles");
        List<String> permissions = values(principal.permissions(), "permissions");

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("campuscore-restful-api")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(principal.id())
                .claim("email", principal.email())
                .claim("roles", roles)
                .claim("permissions", permissions);

        claimIfPresent(claims, "firstName", principal.firstName());
        claimIfPresent(claims, "lastName", principal.lastName());
        claimIfPresent(claims, "status", principal.status());
        claimIfPresent(claims, "studentId", principal.studentId());
        claimIfPresent(claims, "lecturerId", principal.lecturerId());
        if (principal.studentYear() != null) {
            claims.claim("student", Map.of("year", principal.studentYear()));
        }

        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims.build()))
                .getTokenValue();
        return new IssuedAccessToken(token, expiresAt);
    }

    private static List<String> values(List<String> values, String claimName) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> {
                    requireText(value, claimName);
                    return value;
                })
                .toList();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadCredentialsException("Invalid " + field);
        }
    }

    private static void claimIfPresent(JwtClaimsSet.Builder claims, String name, String value) {
        if (value != null) {
            claims.claim(name, value);
        }
    }

    public record IssuedAccessToken(String accessToken, Instant expiresAt) {
    }
}
