package io.campuscore.restfulapi.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.mail.service.EmailService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end contract for the anonymous self-service password reset. The
 * forgot endpoint deliberately returns one identical body for every outcome,
 * so each test asserts the side effects (token rows, mail, audit) rather than
 * the reply — the reply must never differ.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:password_reset;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.public-base-url=https://portal.test"
})
class PasswordResetPersistenceTest {

    /** "taskExecutor" is the bean name @Async falls back to when the unique
     * TaskExecutor lookup is ambiguous — a synchronous one makes the async
     * forgot flow run inline in these tests. */
    @org.springframework.boot.test.context.TestConfiguration
    static class SyncExecutorConfiguration {
        @org.springframework.context.annotation.Bean("taskExecutor")
        org.springframework.core.task.SyncTaskExecutor taskExecutor() {
            return new org.springframework.core.task.SyncTaskExecutor();
        }
    }

    private static final String GENERIC_MESSAGE =
            "If an account matches, a reset link has been sent.";
    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");
    private static final String USER_ID = "reset-user";
    private static final String USER_EMAIL = "reset.user@campuscore.edu";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void prepareFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS campuscore_audit");
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
                    "twoFactorEnabled" BOOLEAN NOT NULL DEFAULT FALSE,
                    "emailVerified" BOOLEAN NOT NULL,
                    "isSuperAdmin" BOOLEAN NOT NULL,
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
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.ensureCredentialReplacementSchema(jdbc);
        jdbc.update("DELETE FROM campuscore_auth.\"TwoFactorChallenge\"");
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
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."PasswordResetToken" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "tokenHash" VARCHAR(64) NOT NULL,
                    "expiresAt" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "usedAt" TIMESTAMP WITH TIME ZONE,
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "requestIp" VARCHAR(64)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS campuscore_audit."AdminAudit" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "actorId" VARCHAR(120),
                    "actorLabel" VARCHAR(240),
                    "action" VARCHAR(48) NOT NULL,
                    "entityType" VARCHAR(48) NOT NULL,
                    "entityId" VARCHAR(120),
                    "summary" VARCHAR(500),
                    "beforeState" TEXT,
                    "afterState" TEXT,
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"PasswordResetToken\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Session\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Student\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Lecturer\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"RolePermission\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Permission\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"UserRole\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Role\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"User\"");
        insertUser(USER_ID, USER_EMAIL, "initial-9pwd", "ACTIVE", false, 0, null);
    }

    // ---------- forgot-password: identical reply, selective side effects ----------

    @Test
    void unknownEmailGetsTheSameReplyAndLeavesNoTrace() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@campuscore.edu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE));

        assertEquals(0, tokenCount());
        assertEquals(0, auditCount("PASSWORD_RESET_REQUESTED"));
        verifyNoInteractions(emailService);
    }

    @Test
    void eligibleUserGetsExactlyOneStoredHashAndAMailedLink() throws Exception {
        requestReset(USER_EMAIL);

        // Only the SHA-256 hex is persisted — never the token itself.
        String storedHash = jdbc.queryForObject(
                "SELECT \"tokenHash\" FROM \"campuscore_auth\".\"PasswordResetToken\"",
                String.class);
        assertEquals(1, tokenCount());
        assertNotNull(storedHash);
        assertTrue(storedHash.matches("[0-9a-f]{64}"), "hash must be 64 lowercase hex");

        String requestIp = jdbc.queryForObject(
                "SELECT \"requestIp\" FROM \"campuscore_auth\".\"PasswordResetToken\"",
                String.class);
        assertNotNull(requestIp);

        // The mailed link carries the raw token whose digest is what we stored.
        String resetLink = capturedResetLink();
        assertTrue(resetLink.startsWith("https://portal.test/reset-password?token="),
                "link must be anchored at the configured public base URL, not headers");
        String rawToken = resetLink.substring(resetLink.indexOf("token=") + 6);
        assertEquals(storedHash, sha256(rawToken));

        assertAudit(USER_ID, "PASSWORD_RESET_REQUESTED");
    }

    @Test
    void privilegedAccountsGetTheGenericReplyWithoutAToken() throws Exception {
        // Privileged accounts are recovered by a super administrator per the
        // runbook — the self-service flow must behave exactly as if the
        // mailbox were unknown.
        insertRole("role-admin", "ADMIN", true);
        insertRole("role-faculty-head", "TRUONG_KHOA", true);
        insertRole("role-department-head", "TRUONG_BO_MON", true);
        insertUser("admin-user", "admin.user@campuscore.edu", "initial-9pwd", "ACTIVE", false, 0, null);
        insertUser("head-user", "head.user@campuscore.edu", "initial-9pwd", "ACTIVE", false, 0, null);
        insertUser("dept-head-user", "depthead.user@campuscore.edu", "initial-9pwd", "ACTIVE", false, 0, null);
        grantRole("admin-user", "role-admin");
        grantRole("head-user", "role-faculty-head");
        grantRole("dept-head-user", "role-department-head");

        requestReset("admin.user@campuscore.edu");
        requestReset("head.user@campuscore.edu");
        requestReset("depthead.user@campuscore.edu");

        assertEquals(0, tokenCount());
        assertEquals(0, auditCount("PASSWORD_RESET_REQUESTED"));
        verifyNoInteractions(emailService);
    }

    @Test
    void inactiveAccountGetsTheGenericReplyWithoutAToken() throws Exception {
        insertUser("disabled-user", "disabled@campuscore.edu", "initial-9pwd",
                "DISABLED", false, 0, null);

        requestReset("disabled@campuscore.edu");

        assertEquals(0, tokenCount());
        verifyNoInteractions(emailService);
    }

    @Test
    void aSecondRequestInvalidatesTheFirstToken() throws Exception {
        requestReset(USER_EMAIL);
        requestReset(USER_EMAIL);

        assertEquals(2, tokenCount());
        Integer live = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"PasswordResetToken\""
                        + " WHERE \"userId\" = ? AND \"usedAt\" IS NULL",
                Integer.class, USER_ID);
        assertEquals(1, live, "only the newest link may stay redeemable");
        verify(emailService, times(2)).sendAsyncHtmlEmail(
                eq(USER_EMAIL), anyString(), eq("password-reset"), anyMap());
    }

    @Test
    void theHourlyCapSilentlySkipsTheFourthRequest() throws Exception {
        requestReset(USER_EMAIL);
        requestReset(USER_EMAIL);
        requestReset(USER_EMAIL);
        assertEquals(3, tokenCount());

        // Fourth request: same 200, but no new token, no mail, no audit row.
        requestReset(USER_EMAIL);
        assertEquals(3, tokenCount());
        assertEquals(3, auditCount("PASSWORD_RESET_REQUESTED"));
        verify(emailService, times(3)).sendAsyncHtmlEmail(
                eq(USER_EMAIL), anyString(), eq("password-reset"), anyMap());
    }

    // ---------- reset-password: consume once, rotate everything ----------

    @Test
    void validTokenRotatesTheCredentialAndRevokesEverything() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var oldLogin = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + USER_EMAIL + "\",\"password\":\"initial-9pwd\"}"))
                .andExpect(status().isOk()).andReturn();
        String oldAccess = mapper.readTree(oldLogin.getResponse().getContentAsString()).get("accessToken").asText();
        // Start from the worst account state: locked out and flagged for
        // mandatory rotation, with a live refresh session. A reset must fix all of it.
        jdbc.update(
                "UPDATE \"campuscore_auth\".\"User\" SET \"mustChangePassword\" = TRUE,"
                        + " \"failedLoginAttempts\" = 5, \"lockedUntil\" = ?,"
                        + " \"refreshToken\" = 'stale-refresh'"
                        + " WHERE \"id\" = ?",
                localDateTime(Instant.now().plusSeconds(3600)), USER_ID);
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"Session\""
                        + " (\"id\", \"userId\", \"refreshToken\", \"expiresAt\", \"createdAt\")"
                        + " VALUES (?, ?, ?, ?, ?)",
                "session-1", USER_ID, "live-refresh",
                localDateTime(Instant.now().plusSeconds(86400)),
                localDateTime(BASE_TIME));

        String token = issueTokenViaEndpoint();
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.pendingLoginChallenge(jdbc, "reset-pending-login", USER_ID);
        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"NewC0rrectHorse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset."));

        Map<String, Object> state = jdbc.queryForMap(
                "SELECT \"failedLoginAttempts\", \"lockedUntil\", \"mustChangePassword\", \"refreshToken\""
                        + " FROM \"campuscore_auth\".\"User\" WHERE \"id\" = ?",
                USER_ID);
        assertEquals(0, ((Number) state.get("failedLoginAttempts")).intValue());
        assertNull(state.get("lockedUntil"));
        assertEquals(Boolean.FALSE, state.get("mustChangePassword"));
        assertNull(state.get("refreshToken"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = ?",
                Integer.class, USER_ID));
        assertNotNull(jdbc.queryForObject(
                "SELECT \"usedAt\" FROM \"campuscore_auth\".\"PasswordResetToken\" WHERE \"userId\" = ?",
                Object.class, USER_ID));

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + oldAccess))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_INVALID"));
        mvc.perform(get("/api/v1/auth/me").cookie(oldLogin.getResponse().getCookie("cc_access_token")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_INVALID"));
        assertEquals(1L, jdbc.queryForObject("SELECT \"credentialVersion\" FROM campuscore_auth.\"User\" WHERE \"id\"=?",
                Long.class, USER_ID));
        assertNotNull(jdbc.queryForObject("SELECT \"consumedAt\" FROM campuscore_auth.\"TwoFactorChallenge\" WHERE \"id\"='reset-pending-login'",
                Object.class));

        // Only the new credential authenticates.
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + USER_EMAIL + "\",\"password\":\"initial-9pwd\"}"))
                .andExpect(status().isUnauthorized());
        var freshLogin = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + USER_EMAIL + "\",\"password\":\"NewC0rrectHorse\"}"))
                .andExpect(status().isOk()).andReturn();
        String freshAccess = mapper.readTree(freshLogin.getResponse().getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + freshAccess)).andExpect(status().isOk());

        // No session was issued by the reset itself: the reply is a bare message.
        assertAudit(USER_ID, "PASSWORD_RESET_COMPLETED");
    }

    @Test
    void consumedTokenCannotBeUsedTwice() throws Exception {
        String token = issueTokenViaEndpoint();
        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"NewC0rrectHorse\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"An0therHorse\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void expiredTokenIsRejectedLikeAnUnknownOne() throws Exception {
        String rawToken = "expired-token-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"PasswordResetToken\""
                        + " (\"id\", \"userId\", \"tokenHash\", \"expiresAt\", \"usedAt\", \"createdAt\")"
                        + " VALUES (?, ?, ?, ?, NULL, ?)",
                UUID.randomUUID().toString(), USER_ID, sha256(rawToken),
                offsetDateTime(Instant.now().minusSeconds(60)),
                offsetDateTime(Instant.now().minusSeconds(3600)));

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"NewC0rrectHorse\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"))
                .andExpect(jsonPath("$.message").value("This reset link is invalid or has expired."));
    }

    @Test
    void policyFailureKeepsTheTokenUsable() throws Exception {
        String token = issueTokenViaEndpoint();

        // A weak candidate is refused by the policy WITHOUT consuming the
        // link — "onlyletters" passes bean validation then fails the
        // letter+digit rule, exercising the pre-consume check.
        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"onlyletters\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_WEAK"));

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"NewC0rrectHorse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset."));
    }

    @Test
    void reusedCurrentPasswordIsRefusedWithoutConsuming() throws Exception {
        String token = issueTokenViaEndpoint();
        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"initial-9pwd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_REUSED"));

        // The link survives: a genuinely new password still redeems it.
        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"NewC0rrectHorse\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void tokenForAPrivilegedOrInactiveUserIsRejected() throws Exception {
        // The account went privileged after the link was issued — the consume
        // path re-checks eligibility, so the token must die on the way in.
        String token = issueTokenViaEndpoint();
        insertRole("role-admin", "ADMIN", true);
        grantRole(USER_ID, "role-admin");

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"NewC0rrectHorse\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));

        // Same contract when the account was deactivated mid-flight.
        jdbc.update("DELETE FROM \"campuscore_auth\".\"UserRole\" WHERE \"userId\" = ?", USER_ID);
        jdbc.update("UPDATE \"campuscore_auth\".\"User\" SET \"status\" = 'DISABLED' WHERE \"id\" = ?", USER_ID);
        String staleToken = "stale-token-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"PasswordResetToken\""
                        + " (\"id\", \"userId\", \"tokenHash\", \"expiresAt\", \"usedAt\", \"createdAt\")"
                        + " VALUES (?, ?, ?, ?, NULL, ?)",
                UUID.randomUUID().toString(), USER_ID, sha256(staleToken),
                offsetDateTime(Instant.now().plusSeconds(1800)),
                offsetDateTime(Instant.now()));

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + staleToken + "\",\"newPassword\":\"NewC0rrectHorse\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void concurrentConsumeLetsExactlyOneThreadWin() throws Exception {
        String token = issueTokenViaEndpoint();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<Integer> first = pool.submit(() -> performReset(token, ready, go));
            Future<Integer> second = pool.submit(() -> performReset(token, ready, go));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "both threads ready");
            go.countDown();
            int statusA = first.get(20, TimeUnit.SECONDS);
            int statusB = second.get(20, TimeUnit.SECONDS);

            int successes = (statusA == 200 ? 1 : 0) + (statusB == 200 ? 1 : 0);
            assertEquals(1, successes,
                    "exactly one consume may win; got " + statusA + " and " + statusB);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void anonymousRequestsWorkAndValidationIsConsistent() throws Exception {
        // No JWT on any of these calls — the endpoints are anonymous.
        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tiny\",\"newPassword\":\"NewC0rrectHorse\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + "x".repeat(40) + "\",\"newPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPasswordIsCappedAtTheTightJsonBodyLimit() throws Exception {
        // The 4 KiB auth-body cap applies: an overlong payload is refused
        // before any user lookup runs.
        String bloated = "{\"email\":\"" + "a".repeat(5 * 1024) + "@x.edu\"}";
        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bloated))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_TOO_LARGE"));
        assertEquals(0, tokenCount());
    }

    // ---------- helpers ----------

    private void requestReset(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE));
    }

    /** Runs the forgot flow and returns the raw token from the mailed link. */
    private String issueTokenViaEndpoint() throws Exception {
        requestReset(USER_EMAIL);
        String resetLink = capturedResetLink();
        return resetLink.substring(resetLink.indexOf("token=") + 6);
    }

    private int performReset(String token, CountDownLatch ready, CountDownLatch go) {
        try {
            ready.countDown();
            go.await(10, TimeUnit.SECONDS);
            return mvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"" + token + "\",\"newPassword\":\"NewC0rrectHorse\"}"))
                    .andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String capturedResetLink() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(emailService, atLeastOnce()).sendAsyncHtmlEmail(
                eq(USER_EMAIL), anyString(), eq("password-reset"), variables.capture());
        // getValue() returns the most recent captured call — the newest link.
        return (String) variables.getValue().get("resetLink");
    }

    private void assertAudit(String userId, String action) {
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\""
                        + " WHERE \"action\" = ? AND \"entityType\" = 'USER'"
                        + " AND \"entityId\" = ? AND \"actorId\" = ?",
                Integer.class, action, userId, userId);
        assertEquals(1, rows, "expected one " + action + " audit row for " + userId);
    }

    private int auditCount(String action) {
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"action\" = ?",
                Integer.class, action);
        return rows == null ? 0 : rows;
    }

    private int tokenCount() {
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"PasswordResetToken\"",
                Integer.class);
        return rows == null ? 0 : rows;
    }

    private void insertUser(String userId, String email, String password, String status,
            boolean mustChange, int failedAttempts, Instant lockedUntil) {
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\""
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\","
                        + " \"mustChangePassword\", \"emailVerified\", \"isSuperAdmin\","
                        + " \"failedLoginAttempts\", \"lockedUntil\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                userId, email, passwordEncoder.encode(password), "Reset", "User", status,
                mustChange, true, false, failedAttempts, localDateTime(lockedUntil),
                localDateTime(BASE_TIME), localDateTime(BASE_TIME));
    }

    private void insertRole(String roleId, String name, boolean isSystem) {
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"Role\""
                        + " (\"id\", \"name\", \"isSystem\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?)",
                roleId, name, isSystem,
                localDateTime(BASE_TIME), localDateTime(BASE_TIME));
    }

    private void grantRole(String userId, String roleId) {
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"UserRole\" (\"id\", \"userId\", \"roleId\")"
                        + " VALUES (?, ?, ?)",
                UUID.randomUUID().toString(), userId, roleId);
    }

    private static LocalDateTime localDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static OffsetDateTime offsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
