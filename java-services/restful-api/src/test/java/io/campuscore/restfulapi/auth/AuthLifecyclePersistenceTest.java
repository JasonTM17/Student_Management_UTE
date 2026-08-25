package io.campuscore.restfulapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.auth.mail.AuthMailDelivery;
import io.campuscore.restfulapi.auth.mail.AuthMailEvent;
import io.campuscore.restfulapi.auth.repository.AuthChallengeRepository.Purpose;
import io.campuscore.restfulapi.auth.service.AuthChallengeTokenService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_lifecycle;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "mail.enabled=false"
})
class AuthLifecyclePersistenceTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthMailDelivery mailDelivery;

    @Test
    void registerVerifyAndLoginEnforcePendingAndSingleUseWithoutAutoLogin() throws Exception {
        String email = "verify.lifecycle@campuscore.test";
        AuthMailEvent verification = registerAndCapture(email);

        assertThat(verification.purpose()).isEqualTo(Purpose.EMAIL_VERIFICATION);
        assertThat(verification.locale()).isEqualTo("vi");
        String storedHash = jdbc.queryForObject(
                "SELECT \"tokenHash\" FROM \"auth\".\"AuthChallenge\" WHERE \"id\" = ?",
                String.class,
                AuthChallengeTokenService.challengeId(verification.rawToken()).orElseThrow());
        assertThat(storedHash)
                .isEqualTo(AuthChallengeTokenService.sha256(verification.rawToken()))
                .doesNotContain(verification.rawToken());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "password123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"));

        mvc.perform(post("/api/v1/auth/email-verifications/confirm")
                        .cookie(new jakarta.servlet.http.Cookie("cc_access_token", "stale"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(verification.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified. Please sign in."))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(cookie().doesNotExist("cc_access_token"))
                .andExpect(cookie().doesNotExist("cc_refresh_token"));

        assertThat(jdbc.queryForObject(
                "SELECT \"emailVerified\" FROM \"auth\".\"User\" WHERE \"email\" = ?",
                Boolean.class,
                email)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT \"status\" FROM \"auth\".\"User\" WHERE \"email\" = ?",
                String.class,
                email)).isEqualTo("ACTIVE");

        mvc.perform(post("/api/v1/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(verification.rawToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_CHALLENGE_INVALID"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.emailVerified").value(true));
    }

    @Test
    void resendInvalidatesPriorVerificationChallenge() throws Exception {
        String email = "resend.lifecycle@campuscore.test";
        AuthMailEvent first = registerAndCapture(email);
        jdbc.update(
                "UPDATE \"auth\".\"AuthChallenge\" SET \"lastSentAt\" = DATEADD('MINUTE', -2, CURRENT_TIMESTAMP)"
                        + " WHERE \"id\" = ?",
                AuthChallengeTokenService.challengeId(first.rawToken()).orElseThrow());
        clearInvocations(mailDelivery);

        mvc.perform(post("/api/v1/auth/email-verifications/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailBody(email)))
                .andExpect(status().isAccepted());
        AuthMailEvent second = captureMail();

        assertThat(second.rawToken()).isNotEqualTo(first.rawToken());
        mvc.perform(post("/api/v1/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(first.rawToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_CHALLENGE_INVALID"));
        mvc.perform(post("/api/v1/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(second.rawToken())))
                .andExpect(status().isOk());
    }

    @Test
    void fifthWrongSecretDisablesChallengeAndCorrectTokenCannotBeReplayed() throws Exception {
        AuthMailEvent event = registerAndCapture("attempts.lifecycle@campuscore.test");
        String challengeId = AuthChallengeTokenService.challengeId(event.rawToken()).orElseThrow();

        for (int attempt = 1; attempt <= 5; attempt++) {
            var assertion = mvc.perform(post("/api/v1/auth/email-verifications/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(tokenBody(challengeId + ".wrong-" + attempt)))
                    .andExpect(attempt < 5 ? status().isBadRequest() : status().isTooManyRequests());
            assertion.andExpect(jsonPath("$.code").value(
                    attempt < 5 ? "AUTH_CHALLENGE_INVALID" : "AUTH_CHALLENGE_ATTEMPTS_EXCEEDED"));
        }

        assertThat(jdbc.queryForObject(
                "SELECT \"attemptCount\" FROM \"auth\".\"AuthChallenge\" WHERE \"id\" = ?",
                Integer.class,
                challengeId)).isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT \"consumedAt\" FROM \"auth\".\"AuthChallenge\" WHERE \"id\" = ?",
                LocalDateTime.class,
                challengeId)).isNotNull();
        mvc.perform(post("/api/v1/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(event.rawToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_CHALLENGE_INVALID"));
    }

    @Test
    void expiredChallengeUsesStableCodeAndIsConsumed() throws Exception {
        AuthMailEvent event = registerAndCapture("expired.lifecycle@campuscore.test");
        String challengeId = AuthChallengeTokenService.challengeId(event.rawToken()).orElseThrow();
        jdbc.update(
                "UPDATE \"auth\".\"AuthChallenge\" SET \"expiresAt\" = DATEADD('MINUTE', -1, CURRENT_TIMESTAMP)"
                        + " WHERE \"id\" = ?",
                challengeId);

        mvc.perform(post("/api/v1/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(event.rawToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_CHALLENGE_EXPIRED"));
        assertThat(jdbc.queryForObject(
                "SELECT \"consumedAt\" FROM \"auth\".\"AuthChallenge\" WHERE \"id\" = ?",
                LocalDateTime.class,
                challengeId)).isNotNull();
    }

    @Test
    void resetRevokesRefreshSessionsClearsLockAndKeepsForgotResponseGeneric() throws Exception {
        String email = "reset.lifecycle@campuscore.test";
        AuthMailEvent verification = registerAndCapture(email);
        mvc.perform(post("/api/v1/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(verification.rawToken())))
                .andExpect(status().isOk());
        clearInvocations(mailDelivery);

        String knownResponse = mvc.perform(post("/api/v1/auth/password-reset-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailBody(email)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        AuthMailEvent reset = captureMail();
        String unknownResponse = mvc.perform(post("/api/v1/auth/password-reset-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailBody("missing.lifecycle@campuscore.test")))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        assertThat(unknownResponse).isEqualTo(knownResponse);

        JsonNode login = objectMapper.readTree(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "password123")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String refreshToken = login.path("refreshToken").asText();
        jdbc.update(
                "UPDATE \"auth\".\"User\" SET \"failedLoginAttempts\" = 5,"
                        + " \"lockedUntil\" = DATEADD('MINUTE', 30, CURRENT_TIMESTAMP) WHERE \"email\" = ?",
                email);

        mvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"new-password-123"}
                                """.formatted(reset.rawToken())))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"auth\".\"Session\" s JOIN \"auth\".\"User\" u"
                        + " ON u.\"id\" = s.\"userId\" WHERE u.\"email\" = ?",
                Integer.class,
                email)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT \"failedLoginAttempts\" FROM \"auth\".\"User\" WHERE \"email\" = ?",
                Integer.class,
                email)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT \"lockedUntil\" FROM \"auth\".\"User\" WHERE \"email\" = ?",
                LocalDateTime.class,
                email)).isNull();

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "password123")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "new-password-123")))
                .andExpect(status().isOk());
    }

    private AuthMailEvent registerAndCapture(String email) throws Exception {
        clearInvocations(mailDelivery);
        mvc.perform(post("/api/v1/auth/register")
                        .header("Accept-Language", "vi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"password123",
                                  "firstName":"Lifecycle",
                                  "lastName":"Student"
                                }
                                """.formatted(email)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.verificationRequired").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(cookie().doesNotExist("cc_access_token"));
        return captureMail();
    }

    private AuthMailEvent captureMail() {
        ArgumentCaptor<AuthMailEvent> event = ArgumentCaptor.forClass(AuthMailEvent.class);
        verify(mailDelivery).send(event.capture());
        return event.getValue();
    }

    private static String tokenBody(String token) {
        return """
                {"token":"%s"}
                """.formatted(token);
    }

    private static String emailBody(String email) {
        return """
                {"email":"%s"}
                """.formatted(email);
    }

    private static String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }
}
