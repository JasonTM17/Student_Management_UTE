package io.campuscore.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.campuscore.auth.service.LegacyAuthUserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private LegacyAuthUserRepository users;

    @Test
    void returnsCurrentActiveUserUsingJwtSubject() {
        UUID userId = UUID.randomUUID();
        AuthUserResponse expected = new AuthUserResponse(
                userId,
                "student@example.edu",
                "Lan",
                "Nguyen",
                null,
                null,
                null,
                null,
                null,
                "ACTIVE",
                Instant.now(),
                List.of("STUDENT"),
                List.of("thesis:group:create"),
                UUID.randomUUID(),
                null);
        when(users.findActiveById(userId)).thenReturn(expected);

        AuthUserResponse actual = new AuthController(users).me(authentication(userId));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void deniesTokenForMissingOrInactiveUser() {
        UUID userId = UUID.randomUUID();
        when(users.findActiveById(userId)).thenReturn(null);

        assertThatThrownBy(() -> new AuthController(users).me(authentication(userId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private JwtAuthenticationToken authentication(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claim("email", "student@example.edu")
                .build();
        return new JwtAuthenticationToken(jwt);
    }
}
