package io.campuscore.restfulapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class AuthTokenServiceTest {

    private static final String SECRET = "test-only-restful-api-secret-with-at-least-32-characters";
    private static final String REFRESH_SECRET = "test-only-refresh-secret-with-at-least-32-characters";
    private static final Instant NOW = Instant.parse("2099-08-20T12:00:00Z");

    @Test
    void issuedAccessTokenDecodesIntoTheSharedClaimContract() {
        SecurityConfig config = new SecurityConfig();
        AuthTokenService service = new AuthTokenService(
                config.jwtEncoder(SECRET),
                config.jwtEncoder(REFRESH_SECRET),
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

        Jwt decoded = config.jwtDecoder(SECRET).decode(issued.accessToken());
        assertEquals("user-1", decoded.getSubject());
        assertEquals("student@campuscore.edu", decoded.getClaimAsString("email"));
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
                config.jwtEncoder(SECRET),
                config.jwtEncoder(REFRESH_SECRET),
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

        Jwt decoded = config.jwtDecoder(REFRESH_SECRET).decode(issued.refreshToken());
        assertEquals("user-1", decoded.getSubject());
        assertEquals("student@campuscore.edu", decoded.getClaimAsString("email"));
        assertEquals("refresh", decoded.getClaimAsString("tokenType"));
        assertEquals(null, decoded.getClaims().get("roles"));
        assertEquals(null, decoded.getClaims().get("permissions"));
        assertEquals(NOW.plus(Duration.ofDays(7)), issued.expiresAt());
    }

    @Test
    void tokenIssuerRejectsInvalidIdentityAndAuthorityClaims() {
        SecurityConfig config = new SecurityConfig();
        AuthTokenService service = new AuthTokenService(
                config.jwtEncoder(SECRET),
                config.jwtEncoder(REFRESH_SECRET),
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
                config.jwtEncoder(SECRET),
                config.jwtEncoder(REFRESH_SECRET),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ZERO,
                Duration.ofDays(7)));

        assertThrows(IllegalArgumentException.class, () -> new AuthTokenService(
                config.jwtEncoder(SECRET),
                config.jwtEncoder(REFRESH_SECRET),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(15),
                Duration.ZERO));
    }
}
