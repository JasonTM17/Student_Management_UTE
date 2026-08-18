package io.campuscore.thesis.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ThesisPermissionEvaluatorTest {

    private final ThesisPermissionEvaluator evaluator = new ThesisPermissionEvaluator();

    @Test
    void deniesWritePermissionWhenClaimIsAbsent() {
        Jwt jwt = jwt(List.of("STUDENT"), List.of("thesis:group:create"));

        assertThat(evaluator.has(new JwtAuthenticationToken(jwt), "thesis:group:approve")).isFalse();
    }

    @Test
    void acceptsTheExactGranularPermission() {
        Jwt jwt = jwt(List.of("LECTURER"), List.of("thesis:review:submit"));

        assertThat(evaluator.has(new JwtAuthenticationToken(jwt), "thesis:review:submit")).isTrue();
    }

    private Jwt jwt(List<String> roles, List<String> permissions) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claims(claims -> {
                    claims.put("roles", roles);
                    claims.put("permissions", permissions);
                })
                .build();
    }
}
