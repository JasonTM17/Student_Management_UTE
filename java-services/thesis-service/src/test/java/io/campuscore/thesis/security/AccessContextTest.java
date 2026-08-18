package io.campuscore.thesis.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AccessContextTest {

    @Test
    void readsLegacyCampusCoreClaimsAndPermissions() {
        UUID userId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claims(claims -> claims.putAll(Map.of(
                        "studentId", studentId.toString(),
                        "roles", List.of("STUDENT"),
                        "permissions", List.of("thesis:group:create"))))
                .build();

        AccessContext context = AccessContext.from(new JwtAuthenticationToken(jwt));

        assertThat(context.userId()).isEqualTo(userId);
        assertThat(context.studentId()).isEqualTo(studentId);
        assertThat(context.hasPermission("thesis:group:create")).isTrue();
        assertThat(context.hasPermission("thesis:round:publish")).isFalse();
    }

    @Test
    void platformAdministratorCanUseThesisPermissionsDuringMigration() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claims(claims -> claims.put("roles", List.of("SUPER_ADMIN")))
                .build();

        assertThat(AccessContext.from(new JwtAuthenticationToken(jwt))
                .hasPermission("thesis:round:publish")).isTrue();
    }
}
