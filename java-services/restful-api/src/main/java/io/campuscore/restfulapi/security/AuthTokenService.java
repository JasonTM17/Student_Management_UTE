package io.campuscore.restfulapi.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/** Issues the Java monolith access token shape without moving auth ownership yet. */
@Service
public class AuthTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtEncoder refreshJwtEncoder;
    private final JwtDecoder refreshJwtDecoder;
    private final Clock clock;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    @Autowired
    public AuthTokenService(
            JwtEncoder jwtEncoder,
            @Value("${security.jwt.refresh-secret:${security.jwt.secret}}") String refreshSecret,
            @Value("${security.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds,
            @Value("${security.jwt.refresh-token-ttl-seconds:604800}") long refreshTokenTtlSeconds) {
        this(
                jwtEncoder,
                refreshEncoder(refreshSecret),
                refreshDecoder(refreshSecret),
                Clock.systemUTC(),
                Duration.ofSeconds(accessTokenTtlSeconds),
                Duration.ofSeconds(refreshTokenTtlSeconds));
    }

    AuthTokenService(
            JwtEncoder jwtEncoder,
            JwtEncoder refreshJwtEncoder,
            JwtDecoder refreshJwtDecoder,
            Clock clock,
            Duration accessTokenTtl,
            Duration refreshTokenTtl) {
        if (accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("Access token TTL must be positive");
        }
        if (refreshTokenTtl.isZero() || refreshTokenTtl.isNegative()) {
            throw new IllegalArgumentException("Refresh token TTL must be positive");
        }
        this.jwtEncoder = jwtEncoder;
        this.refreshJwtEncoder = refreshJwtEncoder;
        this.refreshJwtDecoder = refreshJwtDecoder;
        this.clock = clock;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
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

    public IssuedRefreshToken issueRefreshToken(AuthPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        requireText(principal.id(), "subject");
        requireText(principal.email(), "email");
        values(principal.roles(), "roles");
        values(principal.permissions(), "permissions");

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(refreshTokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("campuscore-restful-api")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .subject(principal.id())
                .claim("email", principal.email())
                .claim("tokenType", "refresh")
                .build();
        String token = refreshJwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims))
                .getTokenValue();
        return new IssuedRefreshToken(token, expiresAt);
    }

    public Jwt decodeRefreshToken(String refreshToken) {
        requireText(refreshToken, "refresh token");
        Jwt decoded = refreshJwtDecoder.decode(refreshToken);
        if (!"refresh".equals(decoded.getClaimAsString("tokenType"))) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        return decoded;
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

    private static JwtEncoder refreshEncoder(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_REFRESH_SECRET must contain at least 32 characters");
        }
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    private static JwtDecoder refreshDecoder(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_REFRESH_SECRET must contain at least 32 characters");
        }
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    public record IssuedAccessToken(String accessToken, Instant expiresAt) {
    }

    public record IssuedRefreshToken(String refreshToken, Instant expiresAt) {
    }
}
