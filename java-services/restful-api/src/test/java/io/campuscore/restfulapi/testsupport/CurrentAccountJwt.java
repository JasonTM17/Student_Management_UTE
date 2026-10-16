package io.campuscore.restfulapi.testsupport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/** Builds endpoint fixtures from existing persisted authority; never seeds or bypasses authentication. */
public final class CurrentAccountJwt {
    private CurrentAccountJwt() { }

    /** Explicit endpoint-test fixture, separate from real-token lifecycle regressions. */
    public static JwtRequestPostProcessor fixtureJwt(JdbcTemplate jdbc, String subject, String... roles) {
        return fixtureJwt(jdbc, subject, token -> { }, roles);
    }

    public static JwtRequestPostProcessor fixtureJwt(JdbcTemplate jdbc, String subject,
            java.util.function.Consumer<org.springframework.security.oauth2.jwt.Jwt.Builder> claims, String... roles) {
        AccountAuthorityFixture.ensureSchema(jdbc);
        AccountAuthorityFixture.ensureUser(jdbc, subject);
        AccountAuthorityFixture.replaceRoles(jdbc, subject, roles);
        return currentJwt(jdbc, subject, claims);
    }

    public static JwtRequestPostProcessor currentJwt(JdbcTemplate jdbc, String subject) {
        return currentJwt(jdbc, subject, token -> { });
    }

    private static JwtRequestPostProcessor currentJwt(JdbcTemplate jdbc, String subject,
            java.util.function.Consumer<org.springframework.security.oauth2.jwt.Jwt.Builder> claims) {
        var state = new AuthUserRepository(new NamedParameterJdbcTemplate(jdbc))
                .findAccountAuthorization(subject).orElseThrow(() -> new IllegalStateException("Missing fixture account: " + subject));
        String email = jdbc.queryForObject("SELECT \"email\" FROM campuscore_auth.\"User\" WHERE \"id\"=?", String.class, subject);
        return jwt().jwt(token -> {
            token.subject(subject).claim("email", email).claim("roles", state.roles()).claim("permissions", state.permissions())
                    .claim("credentialVersion", state.credentialVersion());
            if (state.studentId() != null) token.claim("studentId", state.studentId());
            if (state.studentYear() != null) token.claim("student", Map.of("year", state.studentYear()));
            if (state.lecturerId() != null) token.claim("lecturerId", state.lecturerId());
            claims.accept(token);
        }).authorities(state.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toArray(org.springframework.security.core.GrantedAuthority[]::new));
    }
}
