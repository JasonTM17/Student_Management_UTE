package io.campuscore.restfulapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigTest {

    @Test
    void mapsOnlyWellTypedRoleAndPermissionClaims() {
        Jwt jwt = token()
                .claim("roles", List.of("ADMIN", "LECTURER"))
                .claim("permissions", List.of("announcement:read"))
                .build();

        Set<String> authorities = SecurityConfig.authoritiesFromClaims(jwt).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(
                Set.of("ROLE_ADMIN", "ROLE_LECTURER", "PERM_announcement:read"),
                authorities);
    }

    @Test
    void rejectsScalarMixedAndBlankAuthorityClaims() {
        assertThrows(
                BadCredentialsException.class,
                () -> SecurityConfig.authoritiesFromClaims(
                        token().claim("roles", "ADMIN").build()));
        assertThrows(
                BadCredentialsException.class,
                () -> SecurityConfig.authoritiesFromClaims(
                        token().claim("roles", List.of("ADMIN", 42)).build()));
        assertThrows(
                BadCredentialsException.class,
                () -> SecurityConfig.authoritiesFromClaims(
                        token().claim("permissions", List.of("announcement:read", "")).build()));
    }

    @Test
    void configuresPermissiveLocalAndProductionCors() {
        SecurityConfig config = new SecurityConfig();
        CorsConfigurationSource source = config.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/health");

        CorsConfiguration corsConfig = source.getCorsConfiguration(request);
        assertNotNull(corsConfig);
        assertTrue(Boolean.TRUE.equals(corsConfig.getAllowCredentials()));
        assertTrue(corsConfig.getAllowedOriginPatterns().contains("https://campusute.io.vn"));
        assertTrue(corsConfig.getAllowedOriginPatterns().contains("https://*.vercel.app"));
        assertTrue(corsConfig.getAllowedOriginPatterns().contains("http://localhost:3000"));
    }

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("test-user");
    }
}
