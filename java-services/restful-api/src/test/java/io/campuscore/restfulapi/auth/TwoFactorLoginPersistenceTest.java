package io.campuscore.restfulapi.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.mail.service.EmailService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end contract of the opt-in email OTP two-factor login on H2:
 * enable → confirm → login emits a challenge (no tokens, no cookies) →
 * verify issues the same session as a normal login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:two_factor_login;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class TwoFactorLoginPersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");
    private static final String STUDENT_EMAIL = "student@campuscore.edu";
    private static final String STUDENT_PASSWORD = "password123";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void prepareTwoFactorFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."User" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "email" VARCHAR(320) UNIQUE NOT NULL,
                    "password" VARCHAR(200) NOT NULL,
                    "firstName" VARCHAR(120) NOT NULL,
                    "lastName" VARCHAR(120) NOT NULL,
                    "phone" VARCHAR(80),
                    "gender" VARCHAR(40),
                    "dateOfBirth" TIMESTAMP,
                    "address" VARCHAR(500),
                    "avatar" VARCHAR(500),
                    "status" VARCHAR(40) NOT NULL,
                    "mustChangePassword" BOOLEAN NOT NULL DEFAULT FALSE,
                    "emailVerified" BOOLEAN NOT NULL,
                    "isSuperAdmin" BOOLEAN NOT NULL,
                    "twoFactorEnabled" BOOLEAN NOT NULL DEFAULT FALSE,
                    "failedLoginAttempts" INTEGER NOT NULL,
                    "lockedUntil" TIMESTAMP,
                    "lastLoginAt" TIMESTAMP,
                    "passwordChangedAt" TIMESTAMP,
                    "refreshToken" VARCHAR(200),
                    "resetToken" VARCHAR(200),
                    "resetExpires" TIMESTAMP,
                    "verificationToken" VARCHAR(200),
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."TwoFactorChallenge" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "purpose" VARCHAR(16) NOT NULL,
                    "codeHash" VARCHAR(64) NOT NULL,
                    "expiresAt" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "consumedAt" TIMESTAMP WITH TIME ZONE,
                    "attempts" INTEGER NOT NULL DEFAULT 0,
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."Session" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "refreshToken" VARCHAR(200) NOT NULL,
                    "userAgent" VARCHAR(500),
                    "ipAddress" VARCHAR(80),
                    "expiresAt" TIMESTAMP NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."Student" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) UNIQUE NOT NULL,
                    "studentId" VARCHAR(120) UNIQUE NOT NULL,
                    "curriculumId" VARCHAR(120) NOT NULL,
                    "year" INTEGER NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "admissionDate" TIMESTAMP NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."Lecturer" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) UNIQUE NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL,
                    "employeeId" VARCHAR(120) UNIQUE NOT NULL,
                    "isActive" BOOLEAN NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."Role" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(80) UNIQUE NOT NULL,
                    "description" VARCHAR(500),
                    "isSystem" BOOLEAN NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."Permission" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(160) UNIQUE NOT NULL,
                    "description" VARCHAR(500),
                    "module" VARCHAR(80) NOT NULL,
                    "action" VARCHAR(80) NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."UserRole" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "roleId" VARCHAR(120) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."RolePermission" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "roleId" VARCHAR(120) NOT NULL,
                    "permissionId" VARCHAR(120) NOT NULL
                )
                """);
        jdbc.update("DELETE FROM \"campuscore_auth\".\"TwoFactorChallenge\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Session\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Student\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Lecturer\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"RolePermission\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"UserRole\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Permission\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Role\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"User\"");
        insertStudentUser("student-user", STUDENT_EMAIL);
        reset(emailService);
    }

    @Test
    void statusEndpointReportsTheOptInSwitch() throws Exception {
        mvc.perform(get("/api/v1/me/two-factor").with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        jdbc.update("UPDATE \"campuscore_auth\".\"User\" SET \"twoFactorEnabled\" = TRUE WHERE \"id\" = 'student-user'");

        mvc.perform(get("/api/v1/me/two-factor").with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void enableConfirmThenLoginRequiresSecondFactorAndVerifyIssuesTheSession() throws Exception {
        // 1. Enable: email the ENABLE code, confirm it, switch flips on.
        MvcResult enable = mvc.perform(post("/api/v1/me/two-factor/enable")
                        .with(jwt().jwt(token -> token.subject("student-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + STUDENT_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId", org.hamcrest.Matchers.notNullValue()))
                .andReturn();
        String enableChallengeId = objectMapper
                .readTree(enable.getResponse().getContentAsString())
                .path("challengeId").asText();

        String enableCode = latestCode();
        mvc.perform(post("/api/v1/me/two-factor/confirm")
                        .with(jwt().jwt(token -> token.subject("student-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + enableChallengeId + "\",\"code\":\"" + enableCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        Boolean flag = jdbc.queryForObject(
                "SELECT \"twoFactorEnabled\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'student-user'",
                Boolean.class);
        assertTrue(flag, "twoFactorEnabled must be persisted after confirm");

        // 2. Login now returns a challenge: twoFactorRequired + challengeId +
        // masked email, and explicitly no tokens and no auth cookies.
        MvcResult challenge = loginStudent()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andExpect(jsonPath("$.challengeId", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.email").value("s***@campuscore.edu"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(cookie().doesNotExist("cc_access_token"))
                .andExpect(cookie().doesNotExist("cc_refresh_token"))
                .andReturn();
        String loginChallengeId = objectMapper
                .readTree(challenge.getResponse().getContentAsString())
                .path("challengeId").asText();

        Integer loginChallenges = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"TwoFactorChallenge\""
                        + " WHERE \"userId\" = 'student-user' AND \"purpose\" = 'LOGIN'",
                Integer.class);
        assertEquals(1, loginChallenges);

        // 3. Verify: same session contract as a normal login.
        String loginCode = latestCode();
        MvcResult verified = mvc.perform(post("/api/v1/auth/two-factor/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "jest-java-2fa-verify")
                        .content("{\"challengeId\":\"" + loginChallengeId + "\",\"code\":\"" + loginCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("student-user"))
                .andExpect(jsonPath("$.user.email").value(STUDENT_EMAIL))
                .andExpect(jsonPath("$.twoFactorRequired").doesNotExist())
                .andExpect(jsonPath("$.accessToken", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.refreshToken", org.hamcrest.Matchers.notNullValue()))
                .andExpect(cookie().value("cc_access_token", org.hamcrest.Matchers.notNullValue()))
                .andExpect(cookie().value("cc_refresh_token", org.hamcrest.Matchers.notNullValue()))
                .andReturn();

        JsonNode body = objectMapper.readTree(verified.getResponse().getContentAsString());
        String accessToken = body.get("accessToken").asText();
        jwtDecoder.decode(accessToken);

        // 4. The issued token authenticates business calls.
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("student-user"));

        Integer liveSessions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'student-user'",
                Integer.class);
        assertEquals(1, liveSessions);

        // The consumed challenge cannot be replayed.
        mvc.perform(post("/api/v1/auth/two-factor/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + loginChallengeId + "\",\"code\":\"" + loginCode + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CHALLENGE_INVALID"));
    }

    @Test
    void enableChallengeLocksAfterFiveWrongAttemptsEvenWhenTheRightCodeArrivesLater() throws Exception {
        MvcResult enable = mvc.perform(post("/api/v1/me/two-factor/enable")
                        .with(jwt().jwt(token -> token.subject("student-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + STUDENT_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String enableChallengeId = objectMapper
                .readTree(enable.getResponse().getContentAsString())
                .path("challengeId").asText();
        latestCode();

        // Attempts 1-4: wrong code -> TWO_FACTOR_CODE_INVALID; each attempt must
        // persist across the transaction boundary (Kongming condition on the
        // ENABLE branch).
        for (int attempt = 1; attempt <= 4; attempt++) {
            mvc.perform(post("/api/v1/me/two-factor/confirm")
                            .with(jwt().jwt(token -> token.subject("student-user")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"challengeId\":\"" + enableChallengeId + "\",\"code\":\"000000\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_INVALID"));
        }

        // Attempt 5: the challenge locks.
        mvc.perform(post("/api/v1/me/two-factor/confirm")
                        .with(jwt().jwt(token -> token.subject("student-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + enableChallengeId + "\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_LOCKED"));

        // Even the correct code is refused afterwards, and the flag stays off.
        mvc.perform(post("/api/v1/me/two-factor/confirm")
                        .with(jwt().jwt(token -> token.subject("student-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + enableChallengeId + "\",\"code\":\"" + latestCode() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CHALLENGE_INVALID"));

        Boolean flag = jdbc.queryForObject(
                "SELECT \"twoFactorEnabled\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'student-user'",
                Boolean.class);
        assertFalse(flag, "twoFactorEnabled must stay off after a locked enable challenge");
    }

    @Test
    void fiveWrongCodesLockTheChallengeAndEvenTheCorrectCodeIsRejected() throws Exception {
        enableTwoFactorDirectly();
        MvcResult challenge = loginStudent().andReturn();
        String loginChallengeId = objectMapper
                .readTree(challenge.getResponse().getContentAsString())
                .path("challengeId").asText();
        String correctCode = latestCode();

        for (int attempt = 1; attempt <= 4; attempt++) {
            mvc.perform(post("/api/v1/auth/two-factor/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"challengeId\":\"" + loginChallengeId
                                    + "\",\"code\":\"00000" + attempt + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_INVALID"));
        }

        mvc.perform(post("/api/v1/auth/two-factor/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + loginChallengeId + "\",\"code\":\"999999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_LOCKED"));

        Integer attempts = jdbc.queryForObject(
                "SELECT \"attempts\" FROM \"campuscore_auth\".\"TwoFactorChallenge\" WHERE \"id\" = ?",
                Integer.class,
                loginChallengeId);
        Object consumedAt = jdbc.queryForObject(
                "SELECT \"consumedAt\" FROM \"campuscore_auth\".\"TwoFactorChallenge\" WHERE \"id\" = ?",
                Object.class,
                loginChallengeId);
        assertEquals(5, attempts);
        assertNotNull(consumedAt, "a locked challenge must be consumed");

        // Even the correct code no longer works once locked.
        mvc.perform(post("/api/v1/auth/two-factor/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + loginChallengeId + "\",\"code\":\"" + correctCode + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CHALLENGE_INVALID"));
    }

    @Test
    void expiredChallengeIsRejectedWithCodeExpired() throws Exception {
        enableTwoFactorDirectly();
        MvcResult challenge = loginStudent().andReturn();
        String loginChallengeId = objectMapper
                .readTree(challenge.getResponse().getContentAsString())
                .path("challengeId").asText();
        String correctCode = latestCode();

        jdbc.update(
                "UPDATE \"campuscore_auth\".\"TwoFactorChallenge\" SET \"expiresAt\" = ? WHERE \"id\" = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                loginChallengeId);

        mvc.perform(post("/api/v1/auth/two-factor/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + loginChallengeId + "\",\"code\":\"" + correctCode + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_EXPIRED"));
    }

    @Test
    void externallyWrittenExpiryIsHonouredRegardlessOfJvmTimezone() throws Exception {
        // Regression for the TZ conversion defect (Wukong, 2026-09-26): an
        // external writer stamps expiresAt with plain SQL relative to the
        // database clock. The repository must honour the stored instant — the
        // previous toLocalDateTime()/UTC round trip shifted it by the JVM
        // offset and kept an expired challenge verifiable for +TZ hours.
        enableTwoFactorDirectly();
        MvcResult challenge = loginStudent().andReturn();
        String loginChallengeId = objectMapper
                .readTree(challenge.getResponse().getContentAsString())
                .path("challengeId").asText();
        String correctCode = latestCode();

        jdbc.update(
                "UPDATE \"campuscore_auth\".\"TwoFactorChallenge\""
                        + " SET \"expiresAt\" = now() - INTERVAL '11' MINUTE WHERE \"id\" = ?",
                loginChallengeId);

        mvc.perform(post("/api/v1/auth/two-factor/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "jest-java-2fa-tz")
                        .content("{\"challengeId\":\"" + loginChallengeId + "\",\"code\":\"" + correctCode + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_EXPIRED"));
    }

    @Test
    void disableRestoresTheSingleStepLoginContract() throws Exception {
        enableTwoFactorDirectly();
        MvcResult challenge = loginStudent()
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andReturn();
        assertNotNull(challenge);

        mvc.perform(post("/api/v1/me/two-factor/disable")
                        .with(jwt().jwt(token -> token.subject("student-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + STUDENT_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // Byte-compatible single-step login again: tokens present, no
        // two-factor fields in the payload.
        loginStudent()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("student-user"))
                .andExpect(jsonPath("$.accessToken", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.refreshToken", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.twoFactorRequired").doesNotExist())
                .andExpect(jsonPath("$.challengeId").doesNotExist())
                .andExpect(jsonPath("$.maskedEmail").doesNotExist());
    }

    @Test
    void enableWithWrongPasswordIsRejectedAndStoresNoChallenge() throws Exception {
        mvc.perform(post("/api/v1/me/two-factor/enable")
                        .with(jwt().jwt(token -> token.subject("student-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong-" + java.util.UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid password"));

        Integer challenges = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"TwoFactorChallenge\"",
                Integer.class);
        assertEquals(0, challenges, "a failed password check must not leave a challenge behind");
    }

    @Test
    void verifyRejectsUnknownAndCrossPurposeChallenges() throws Exception {
        // Unknown challenge id.
        mvc.perform(post("/api/v1/auth/two-factor/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + java.util.UUID.randomUUID()
                                + "\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CHALLENGE_INVALID"));

        // An ENABLE challenge must not unlock a login.
        MvcResult enable = mvc.perform(post("/api/v1/me/two-factor/enable")
                        .with(jwt().jwt(token -> token.subject("student-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + STUDENT_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String enableChallengeId = objectMapper
                .readTree(enable.getResponse().getContentAsString())
                .path("challengeId").asText();
        String enableCode = latestCode();

        mvc.perform(post("/api/v1/auth/two-factor/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + enableChallengeId + "\",\"code\":\"" + enableCode + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CHALLENGE_INVALID"));

        // Another account's ENABLE challenge is invisible to confirm.
        insertStudentUser("second-user", "second@campuscore.edu");
        MvcResult secondEnable = mvc.perform(post("/api/v1/me/two-factor/enable")
                        .with(jwt().jwt(token -> token.subject("second-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String secondChallengeId = objectMapper
                .readTree(secondEnable.getResponse().getContentAsString())
                .path("challengeId").asText();
        String secondCode = latestCode();

        mvc.perform(post("/api/v1/me/two-factor/confirm")
                        .with(jwt().jwt(token -> token.subject("student-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + secondChallengeId + "\",\"code\":\"" + secondCode + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CHALLENGE_INVALID"));
    }

    @Test
    void confirmingEnableTwiceStillYieldsEnabledAndChallengeCannotBeReused() throws Exception {
        MvcResult enable = mvc.perform(post("/api/v1/me/two-factor/enable")
                        .with(jwt().jwt(token -> token.subject("student-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + STUDENT_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String challengeId = objectMapper
                .readTree(enable.getResponse().getContentAsString())
                .path("challengeId").asText();
        String code = latestCode();

        mvc.perform(post("/api/v1/me/two-factor/confirm")
                        .with(jwt().jwt(token -> token.subject("student-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + challengeId + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        mvc.perform(post("/api/v1/me/two-factor/confirm")
                        .with(jwt().jwt(token -> token.subject("student-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + challengeId + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CHALLENGE_INVALID"));

        Boolean flag = jdbc.queryForObject(
                "SELECT \"twoFactorEnabled\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'student-user'",
                Boolean.class);
        assertTrue(flag);
    }

    /** Turns the switch on directly; the API flow is covered by lifecycle tests. */
    private void enableTwoFactorDirectly() {
        jdbc.update(
                "UPDATE \"campuscore_auth\".\"User\" SET \"twoFactorEnabled\" = TRUE WHERE \"id\" = 'student-user'");
    }

    /** Reads the six-digit code the service handed to the (mocked) mail sender. */
    private String latestCode() {
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(emailService, atLeastOnce()).sendHtmlEmail(
                anyString(), anyString(), eq("two-factor-code"), variables.capture());
        List<Map<String, Object>> allValues = variables.getAllValues();
        Object code = allValues.get(allValues.size() - 1).get("code");
        assertNotNull(code, "the mail variables must carry the code");
        return code.toString();
    }

    private org.springframework.test.web.servlet.ResultActions loginStudent() throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "jest-java-2fa-login")
                .content("{\"email\":\"" + STUDENT_EMAIL + "\",\"password\":\"" + STUDENT_PASSWORD + "\"}"));
    }

    private void insertStudentUser(String userId, String email) {
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\""
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\","
                        + " \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"lockedUntil\","
                        + " \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                userId,
                email,
                passwordEncoder.encode(STUDENT_PASSWORD),
                "Student",
                "One",
                "ACTIVE",
                true,
                false,
                0,
                null,
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME));
        Integer roleCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Role\" WHERE \"id\" = 'role-student'",
                Integer.class);
        if (roleCount == 0) {
            jdbc.update(
                    "INSERT INTO \"campuscore_auth\".\"Role\""
                            + " (\"id\", \"name\", \"description\", \"isSystem\", \"createdAt\", \"updatedAt\")"
                            + " VALUES (?, ?, ?, ?, ?, ?)",
                    "role-student",
                    "STUDENT",
                    null,
                    true,
                    localDateTime(BASE_TIME),
                    localDateTime(BASE_TIME));
        }
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"Permission\""
                        + " (\"id\", \"name\", \"description\", \"module\", \"action\", \"createdAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                "permission-" + userId,
                "thesis.read." + userId,
                null,
                "thesis",
                "read",
                localDateTime(BASE_TIME));
        jdbc.update("INSERT INTO \"campuscore_auth\".\"UserRole\" (\"id\", \"userId\", \"roleId\") VALUES (?, ?, ?)",
                "user-role-" + userId, userId, "role-student");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"RolePermission\" (\"id\", \"roleId\", \"permissionId\") VALUES (?, ?, ?)",
                "role-permission-" + userId, "role-student", "permission-" + userId);
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"Student\""
                        + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                        + " \"admissionDate\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "student-profile-" + userId,
                userId,
                "S-" + userId,
                "curriculum-1",
                2,
                "ACTIVE",
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME));
    }

    private static LocalDateTime localDateTime(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
