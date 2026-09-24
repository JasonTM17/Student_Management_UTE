package io.campuscore.restfulapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class AuthTokenServiceTest {

    private static final String SECRET = "test-only-restful-api-secret-with-at-least-32-characters";
    private static final String REFRESH_SECRET = "test-only-refresh-secret-with-at-least-32-characters";
    private static final Instant NOW = Instant.parse("2099-08-20T12:00:00Z");

    @Test
    void issuedAccessTokenDecodesIntoTheSharedClaimContract() {
        SecurityConfig config = new SecurityConfig();
        AuthTokenService service = new AuthTokenService(
                config.jwtEncoder(SECRET, true),
                config.jwtEncoder(REFRESH_SECRET, true),
                config.jwtDecoder(REFRESH_SECRET, true),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(15),
                Duration.ofDays(7));

        AuthTokenService.IssuedAccessToken issued = service.issueAccessToken(new AuthPrincipal(
                "user-1",
                "student@campuscore.edu",
                "Student",
                "One",
                "ACTIVE",
                List.of("STUDENT"),
                List.of("thesis:read"),
                "student-1",
                2,
                null));

        Jwt decoded = config.jwtDecoder(SECRET, true).decode(issued.accessToken());
        assertEquals("user-1", decoded.getSubject());
        assertEquals("student@campuscore.edu", decoded.getClaimAsString("email"));
        assertEquals("access", decoded.getClaimAsString("tokenType"));
        assertEquals(List.of("STUDENT"), decoded.getClaimAsStringList("roles"));
        assertEquals(List.of("thesis:read"), decoded.getClaimAsStringList("permissions"));
        assertEquals("student-1", decoded.getClaimAsString("studentId"));
        assertEquals(2, ((Number) decoded.<Map<String, Object>>getClaim("student").get("year")).intValue());
        assertEquals(NOW.plus(Duration.ofMinutes(15)), issued.expiresAt());

        Set<String> authorities = SecurityConfig.authoritiesFromClaims(decoded).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("ROLE_STUDENT", "PERM_thesis:read"), authorities);
    }

    @Test
    void issuedRefreshTokenUsesDedicatedSecretAndMinimalClaims() {
        SecurityConfig config = new SecurityConfig();
        AuthTokenService service = new AuthTokenService(
                config.jwtEncoder(SECRET, true),
                config.jwtEncoder(REFRESH_SECRET, true),
                config.jwtDecoder(REFRESH_SECRET, true),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(15),
                Duration.ofDays(7));

        AuthTokenService.IssuedRefreshToken issued = service.issueRefreshToken(new AuthPrincipal(
                "user-1",
                "student@campuscore.edu",
                "Student",
                "One",
                "ACTIVE",
                List.of("STUDENT"),
                List.of("thesis:read"),
                "student-1",
                2,
                null));

        // The shared jwtDecoder bean is access-only now, so inspect refresh
        // claims through the same plain decoder the service's refresh path uses.
        Jwt decoded = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(
                        REFRESH_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build()
                .decode(issued.refreshToken());
        assertEquals("user-1", decoded.getSubject());
        assertEquals("student@campuscore.edu", decoded.getClaimAsString("email"));
        assertEquals("refresh", decoded.getClaimAsString("tokenType"));
        assertTrue(decoded.getId() != null && !decoded.getId().isBlank());
        assertEquals(null, decoded.getClaims().get("roles"));
        assertEquals(null, decoded.getClaims().get("permissions"));
        assertEquals(NOW.plus(Duration.ofDays(7)), issued.expiresAt());
    }

    @Test
    void refreshTokenIsRefusedByTheAccessTokenDecoderEvenWithSharedSecret() {
        SecurityConfig config = new SecurityConfig();
        // Deliberately sign BOTH token families with the same secret: this is
        // the misconfiguration where the tokenType discriminator is the only
        // remaining separation between the two families.
        AuthTokenService service = new AuthTokenService(
                config.jwtEncoder(SECRET, true),
                config.jwtEncoder(SECRET, true),
                config.jwtDecoder(SECRET, true),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(15),
                Duration.ofDays(7));

        AuthTokenService.IssuedRefreshToken issued = service.issueRefreshToken(new AuthPrincipal(
                "user-1",
                "student@campuscore.edu",
                "Student",
                "One",
                "ACTIVE",
                List.of("STUDENT"),
                List.of(),
                "student-1",
                2,
                null));

        // The signature validates (same secret), so without the decoder-side
        // tokenType check this decode succeeds and the refresh token rides the
        // Authorization header — the exact regression this test pins.
        assertThrows(BadCredentialsException.class, () -> config.jwtDecoder(SECRET, true).decode(issued.refreshToken()));
    }

    @Test
    void accessTokenStillDecodesThroughTheShapeValidatingDecoder() {
        SecurityConfig config = new SecurityConfig();
        AuthTokenService service = new AuthTokenService(
                config.jwtEncoder(SECRET, true),
                config.jwtEncoder(REFRESH_SECRET, true),
                config.jwtDecoder(REFRESH_SECRET, true),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(15),
                Duration.ofDays(7));

        AuthTokenService.IssuedAccessToken issued = service.issueAccessToken(new AuthPrincipal(
                "user-1",
                "student@campuscore.edu",
                "Student",
                "One",
                "ACTIVE",
                List.of("STUDENT"),
                List.of(),
                null,
                null,
                null));

        Jwt decoded = config.jwtDecoder(SECRET, true).decode(issued.accessToken());
        assertEquals("user-1", decoded.getSubject());
    }

    @Test
    void tokenIssuerRejectsInvalidIdentityAndAuthorityClaims() {
        SecurityConfig config = new SecurityConfig();
        AuthTokenService service = new AuthTokenService(
                config.jwtEncoder(SECRET, true),
                config.jwtEncoder(REFRESH_SECRET, true),
                config.jwtDecoder(REFRESH_SECRET, true),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(15),
                Duration.ofDays(7));

        assertThrows(BadCredentialsException.class, () -> service.issueAccessToken(new AuthPrincipal(
                "",
                "student@campuscore.edu",
                null,
                null,
                "ACTIVE",
                List.of("STUDENT"),
                List.of(),
                null,
                null,
                null)));

        assertThrows(BadCredentialsException.class, () -> service.issueAccessToken(new AuthPrincipal(
                "user-1",
                "student@campuscore.edu",
                null,
                null,
                "ACTIVE",
                List.of("STUDENT", " "),
                List.of(),
                null,
                null,
                null)));
    }

    @Test
    void tokenIssuerRejectsNonPositiveTtl() {
        SecurityConfig config = new SecurityConfig();

        assertThrows(IllegalArgumentException.class, () -> new AuthTokenService(
                config.jwtEncoder(SECRET, true),
                config.jwtEncoder(REFRESH_SECRET, true),
                config.jwtDecoder(REFRESH_SECRET, true),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ZERO,
                Duration.ofDays(7)));

        assertThrows(IllegalArgumentException.class, () -> new AuthTokenService(
                config.jwtEncoder(SECRET, true),
                config.jwtEncoder(REFRESH_SECRET, true),
                config.jwtDecoder(REFRESH_SECRET, true),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(15),
                Duration.ZERO));
    }
}
