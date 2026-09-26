package io.campuscore.restfulapi.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:auth_login;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class AuthLoginPersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

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

    @BeforeEach
    void prepareAuthFixture() {
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
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Session\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Student\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Lecturer\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"RolePermission\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"UserRole\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Permission\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Role\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"User\"");
        insertStudentUser("student-user", "student@campuscore.edu", "password123", 0, null);
    }

    @Test
    void publicRegistrationEndpointIsRemovedAndCreatesNoUser() throws Exception {
        // Accounts are issued by the Academic Office: the public contract is
        // gone entirely, so an anonymous call is rejected as unauthenticated
        // instead of reaching any registration handler. The payload is
        // irrelevant now — it must never reach a handler or create a user.
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"new.student@campuscore.edu",
                                  "firstName":"New",
                                  "lastName":"Student"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"User\" WHERE \"email\" = ?",
                Integer.class,
                "new.student@campuscore.edu"));
    }

    @Test
    void adminUserCreationCreatesTheMatchingAcademicProfile() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/users")
                        .with(jwt()
                                .jwt(token -> token.subject("admin-user").claim("roles", java.util.List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"managed.student@campuscore.edu",
                                  "firstName":"Managed",
                                  "lastName":"Student",
                                  "role":"STUDENT",
                                  "studentId":"SV-MANAGED-001",
                                  "curriculumId":"curriculum-demo",
                                  "year":1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("managed.student@campuscore.edu"))
                .andReturn();

        String userId = objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
        Integer profiles = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Student\" WHERE \"userId\" = ?"
                        + " AND \"studentId\" = 'SV-MANAGED-001'",
                Integer.class,
                userId);
        org.junit.jupiter.api.Assertions.assertEquals(1, profiles);
    }

    @Test
    void issuedAccountMustRotateItsTemporarySecretBeforeUsingThePortal() throws Exception {
        // Simulate an office-issued account: a server-generated one-time
        // credential plus the rotation flag. The flag — not any token claim —
        // is what the account-state filter enforces.
        String issuedSecret = "issued-" + UUID.randomUUID();
        String rotatedSecret = "rotated-" + UUID.randomUUID();
        jdbc.update(
                "UPDATE \"campuscore_auth\".\"User\" SET \"password\" = ?, \"mustChangePassword\" = TRUE"
                        + " WHERE \"id\" = 'student-user'",
                passwordEncoder.encode(issuedSecret));

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@campuscore.edu\",\"password\":\"" + issuedSecret + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn();
        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        String accessToken = body.get("accessToken").asText();

        // Business endpoints refuse the issued credential until it is rotated.
        mvc.perform(put("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0900000000\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + issuedSecret
                                + "\",\"newPassword\":\"" + rotatedSecret + "\"}"))
                .andExpect(status().isOk());

        Boolean flag = jdbc.queryForObject(
                "SELECT \"mustChangePassword\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'student-user'",
                Boolean.class);
        org.junit.jupiter.api.Assertions.assertFalse(flag);

        mvc.perform(put("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0900000000\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void loginIssuesBodyTokensCookiesAndPersistsHashedRefreshSession() throws Exception {
        MvcResult result = loginStudent()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("student-user"))
                .andExpect(jsonPath("$.user.email").value("student@campuscore.edu"))
                .andExpect(jsonPath("$.user.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.user.permissions[0]").value("thesis:read"))
                .andExpect(jsonPath("$.user.studentId").value("student-profile-student-user"))
                .andExpect(jsonPath("$.user.student.year").value(2))
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(cookie().value("cc_access_token", notNullValue()))
                .andExpect(cookie().value("cc_refresh_token", notNullValue()))
                .andExpect(cookie().value("cc_csrf", notNullValue()))
                .andExpect(cookie().httpOnly("cc_access_token", true))
                .andExpect(cookie().httpOnly("cc_refresh_token", true))
                .andExpect(cookie().httpOnly("cc_csrf", false))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        jwtDecoder.decode(body.get("accessToken").asText());

        Integer failedAttempts = jdbc.queryForObject(
                "SELECT \"failedLoginAttempts\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'student-user'",
                Integer.class);
        Integer sessions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'student-user'",
                Integer.class);
        String storedRefresh = jdbc.queryForObject(
                "SELECT \"refreshToken\" FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'student-user'",
                String.class);

        org.junit.jupiter.api.Assertions.assertEquals(0, failedAttempts);
        org.junit.jupiter.api.Assertions.assertEquals(1, sessions);
        org.junit.jupiter.api.Assertions.assertEquals(64, storedRefresh.length());
    }

    @Test
    void refreshRequiresCookieCsrfAndRotatesTheStoredRefreshSession() throws Exception {
        MvcResult login = loginStudent().andReturn();
        JsonNode loginBody = objectMapper.readTree(login.getResponse().getContentAsString());
        String oldRefreshToken = loginBody.get("refreshToken").asText();
        Cookie refreshCookie = login.getResponse().getCookie("cc_refresh_token");
        Cookie csrfCookie = login.getResponse().getCookie("cc_csrf");

        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie, csrfCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        MvcResult refresh = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie, csrfCookie)
                        .header("X-CSRF-Token", csrfCookie.getValue())
                        .header("User-Agent", "jest-java-refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("student-user"))
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(cookie().value("cc_access_token", notNullValue()))
                .andExpect(cookie().value("cc_refresh_token", notNullValue()))
                .andExpect(cookie().value("cc_csrf", notNullValue()))
                .andReturn();

        JsonNode refreshBody = objectMapper.readTree(refresh.getResponse().getContentAsString());
        String newRefreshToken = refreshBody.get("refreshToken").asText();
        assertNotEquals(oldRefreshToken, newRefreshToken);

        Integer sessions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'student-user'",
                Integer.class);
        String storedRefresh = jdbc.queryForObject(
                "SELECT \"refreshToken\" FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'student-user'",
                String.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, sessions);
        org.junit.jupiter.api.Assertions.assertNotEquals(sha256(oldRefreshToken), storedRefresh);
        org.junit.jupiter.api.Assertions.assertEquals(sha256(newRefreshToken), storedRefresh);
    }

    /**
     * Regression for the P0 logout bug: a second login used to delete every
     * earlier refresh session (replaceRefreshSession wiped all rows per user),
     * so the first device was kicked out at its next access-token expiry.
     */
    @Test
    void secondLoginKeepsTheFirstSessionRefreshable() throws Exception {
        MvcResult firstLogin = loginStudent().andReturn();
        JsonNode firstBody = objectMapper.readTree(firstLogin.getResponse().getContentAsString());
        String firstRefreshToken = firstBody.get("refreshToken").asText();
        Cookie firstRefreshCookie = firstLogin.getResponse().getCookie("cc_refresh_token");
        Cookie firstCsrfCookie = firstLogin.getResponse().getCookie("cc_csrf");

        MvcResult secondLogin = loginStudent().andReturn();
        JsonNode secondBody = objectMapper.readTree(secondLogin.getResponse().getContentAsString());
        String secondRefreshToken = secondBody.get("refreshToken").asText();
        assertNotEquals(firstRefreshToken, secondRefreshToken);

        Integer sessionsAfterSecondLogin = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'student-user'",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(2, sessionsAfterSecondLogin);

        MvcResult firstDeviceRefresh = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(firstRefreshCookie, firstCsrfCookie)
                        .header("X-CSRF-Token", firstCsrfCookie.getValue())
                        .header("User-Agent", "jest-java-first-device"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("student-user"))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andReturn();

        String rotatedRefreshToken = objectMapper
                .readTree(firstDeviceRefresh.getResponse().getContentAsString())
                .get("refreshToken")
                .asText();

        Integer sessionsAfterFirstRotation = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'student-user'",
                Integer.class);
        Integer rotatedSessionStored = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'student-user'"
                        + " AND \"refreshToken\" = ?",
                Integer.class,
                sha256(rotatedRefreshToken));
        Integer secondSessionStillActive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'student-user'"
                        + " AND \"refreshToken\" = ?",
                Integer.class,
                sha256(secondRefreshToken));

        org.junit.jupiter.api.Assertions.assertEquals(2, sessionsAfterFirstRotation);
        org.junit.jupiter.api.Assertions.assertEquals(1, rotatedSessionStored);
        org.junit.jupiter.api.Assertions.assertEquals(1, secondSessionStillActive);
    }

    @Test
    void refreshAcceptsBodyRefreshTokenWithoutCookieForMobileClients() throws Exception {
        MvcResult login = loginStudent().andReturn();
        JsonNode loginBody = objectMapper.readTree(login.getResponse().getContentAsString());
        String oldRefreshToken = loginBody.get("refreshToken").asText();

        MvcResult refresh = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "jest-java-mobile-refresh")
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(oldRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("student-user"))
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andReturn();

        JsonNode refreshBody = objectMapper.readTree(refresh.getResponse().getContentAsString());
        String newRefreshToken = refreshBody.get("refreshToken").asText();
        assertNotEquals(oldRefreshToken, newRefreshToken);

        String storedRefresh = jdbc.queryForObject(
                "SELECT \"refreshToken\" FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'student-user'",
                String.class);
        org.junit.jupiter.api.Assertions.assertEquals(sha256(newRefreshToken), storedRefresh);
    }

    /**
     * SEC-P1-1: a refresh token that was already rotated (or revoked) and is
     * then replayed is the classic stolen-token signal. The replay must be
     * refused AND the whole session family for that account must die — the
     * freshly rotated token included — instead of silently 401-ing while the
     * thief keeps the current session.
     */
    @Test
    void replayingARotatedRefreshTokenRevokesTheWholeSessionFamily() throws Exception {
        MvcResult login = loginStudent().andReturn();
        JsonNode loginBody = objectMapper.readTree(login.getResponse().getContentAsString());
        String staleRefreshToken = loginBody.get("refreshToken").asText();
        Cookie refreshCookie = login.getResponse().getCookie("cc_refresh_token");
        Cookie csrfCookie = login.getResponse().getCookie("cc_csrf");

        // Rotate once: the account now holds exactly one live (new) session.
        MvcResult rotation = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie, csrfCookie)
                        .header("X-CSRF-Token", csrfCookie.getValue())
                        .header("User-Agent", "reuse-test-rotate"))
                .andExpect(status().isOk())
                .andReturn();
        String liveRefreshToken = objectMapper
                .readTree(rotation.getResponse().getContentAsString())
                .get("refreshToken")
                .asText();
        assertNotEquals(staleRefreshToken, liveRefreshToken);

        // Replaying the stale token: refused, and the family is revoked.
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "reuse-test-replay")
                        .content("{\"refreshToken\":\"" + staleRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        Integer liveSessions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'student-user'",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, liveSessions);

        // The previously-live rotated token is dead too.
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "reuse-test-after")
                        .content("{\"refreshToken\":\"" + liveRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsCurrentUserForBearerAndCookieSessions() throws Exception {
        MvcResult login = loginStudent().andReturn();
        JsonNode loginBody = objectMapper.readTree(login.getResponse().getContentAsString());
        String accessToken = loginBody.get("accessToken").asText();
        Cookie accessCookie = login.getResponse().getCookie("cc_access_token");

        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("student-user"))
                .andExpect(jsonPath("$.email").value("student@campuscore.edu"))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.permissions[0]").value("thesis:read"))
                .andExpect(jsonPath("$.studentId").value("student-profile-student-user"))
                .andExpect(jsonPath("$.student.year").value(2));

        mvc.perform(get("/api/v1/auth/me").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("student-user"))
                .andExpect(jsonPath("$.email").value("student@campuscore.edu"))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"));
    }

    @Test
    void updateProfileRequiresCookieCsrfAndPersistsLegacyUserFields() throws Exception {
        MvcResult login = loginStudent().andReturn();
        Cookie accessCookie = login.getResponse().getCookie("cc_access_token");
        Cookie csrfCookie = login.getResponse().getCookie("cc_csrf");

        // The school-managed name before anyone tries to change it.
        MvcResult before = mvc.perform(get("/api/v1/auth/me").cookie(accessCookie))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode beforeBody = objectMapper.readTree(before.getResponse().getContentAsString());
        String managedFirstName = beforeBody.path("firstName").asText();
        String managedLastName = beforeBody.path("lastName").asText();

        mvc.perform(put("/api/v1/auth/profile")
                        .cookie(accessCookie, csrfCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Updated","lastName":"Student"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        mvc.perform(put("/api/v1/auth/profile")
                        .cookie(accessCookie, csrfCookie)
                        .header("X-CSRF-Token", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Updated",
                                  "lastName":"Student",
                                  "phone":"+84999999999",
                                  "dateOfBirth":"2001-02-03",
                                  "address":"Java monolith lane"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("student-user"))
                // The full name is school-managed: the payload is ignored.
                .andExpect(jsonPath("$.firstName").value(managedFirstName))
                .andExpect(jsonPath("$.lastName").value(managedLastName))
                .andExpect(jsonPath("$.phone").value("+84999999999"))
                .andExpect(jsonPath("$.address").value("Java monolith lane"))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"));

        mvc.perform(get("/api/v1/auth/me").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value(managedFirstName))
                .andExpect(jsonPath("$.lastName").value(managedLastName))
                .andExpect(jsonPath("$.phone").value("+84999999999"))
                .andExpect(jsonPath("$.address").value("Java monolith lane"));
    }

    @Test
    void updateProfileEnforcesAvatarSizeContract() throws Exception {
        MvcResult login = loginStudent().andReturn();
        Cookie accessCookie = login.getResponse().getCookie("cc_access_token");
        Cookie csrfCookie = login.getResponse().getCookie("cc_csrf");

        // The DTO declares @Size(max = 200_000) on the avatar data URL; the
        // profile endpoint must reject oversized payloads with a 400 before
        // anything is persisted.
        String oversizedAvatar = "data:image/png;base64," + "A".repeat(200_001);
        mvc.perform(put("/api/v1/auth/profile")
                        .cookie(accessCookie, csrfCookie)
                        .header("X-CSRF-Token", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatar\":\"" + oversizedAvatar + "\"}"))
                .andExpect(status().isBadRequest());

        Integer storedAvatarLength = jdbc.queryForObject(
                "SELECT LENGTH(\"avatar\") FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'student-user'",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertTrue(
                storedAvatarLength == null || storedAvatarLength <= 200_000,
                "oversized avatar must not be persisted, got length " + storedAvatarLength);
    }

    @Test
    void changePasswordRequiresAuthenticationUpdatesHashAndRevokesRefreshSessions() throws Exception {
        // Random per-run secrets keep this file free of credential literals
        // while still proving the hash update end to end.
        String oldSecret = "old-" + UUID.randomUUID();
        String newSecret = "new-" + UUID.randomUUID();
        jdbc.update("UPDATE \"campuscore_auth\".\"User\" SET \"password\" = ? WHERE \"id\" = 'student-user'",
                passwordEncoder.encode(oldSecret));
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@campuscore.edu\",\"password\":\"" + oldSecret + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode loginBody = objectMapper.readTree(login.getResponse().getContentAsString());
        String accessToken = loginBody.get("accessToken").asText();

        mvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + oldSecret + "\",\"newPassword\":\"" + newSecret + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + accessToken)
                        .content("{\"oldPassword\":\"wrong-" + UUID.randomUUID()
                                + "\",\"newPassword\":\"" + newSecret + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid old password"));

        mvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + accessToken)
                        .content("{\"oldPassword\":\"" + oldSecret + "\",\"newPassword\":\"" + newSecret + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));

        Integer sessions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'student-user'",
                Integer.class);
        String storedRefresh = jdbc.queryForObject(
                "SELECT \"refreshToken\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'student-user'",
                String.class);
        Object passwordChangedAt = jdbc.queryForObject(
                "SELECT \"passwordChangedAt\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'student-user'",
                Object.class);

        org.junit.jupiter.api.Assertions.assertEquals(0, sessions);
        org.junit.jupiter.api.Assertions.assertNull(storedRefresh);
        org.junit.jupiter.api.Assertions.assertNotNull(passwordChangedAt);

        // The rotated credential must be the only working one.
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@campuscore.edu\",\"password\":\"" + oldSecret + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@campuscore.edu\",\"password\":\"" + newSecret + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("student-user"));
    }

    @Test
    void logoutRequiresCookieCsrfAndClearsTheRefreshSession() throws Exception {
        MvcResult login = loginStudent().andReturn();
        Cookie accessCookie = login.getResponse().getCookie("cc_access_token");
        Cookie refreshCookie = login.getResponse().getCookie("cc_refresh_token");
        Cookie csrfCookie = login.getResponse().getCookie("cc_csrf");

        mvc.perform(post("/api/v1/auth/logout").cookie(accessCookie, refreshCookie, csrfCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        mvc.perform(post("/api/v1/auth/logout")
                        .cookie(accessCookie, refreshCookie, csrfCookie)
                        .header("X-CSRF-Token", csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"))
                .andExpect(cookie().maxAge("cc_access_token", 0))
                .andExpect(cookie().maxAge("cc_refresh_token", 0))
                .andExpect(cookie().maxAge("cc_csrf", 0));

        Integer sessions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'student-user'",
                Integer.class);
        String storedRefresh = jdbc.queryForObject(
                "SELECT \"refreshToken\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'student-user'",
                String.class);

        org.junit.jupiter.api.Assertions.assertEquals(0, sessions);
        org.junit.jupiter.api.Assertions.assertNull(storedRefresh);
    }

    @Test
    void logoutAcceptsBearerAndBodyRefreshTokenForMobileClients() throws Exception {
        MvcResult login = loginStudent().andReturn();
        JsonNode loginBody = objectMapper.readTree(login.getResponse().getContentAsString());
        String accessToken = loginBody.get("accessToken").asText();
        String refreshToken = loginBody.get("refreshToken").asText();

        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + accessToken)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"))
                .andExpect(cookie().maxAge("cc_access_token", 0))
                .andExpect(cookie().maxAge("cc_refresh_token", 0))
                .andExpect(cookie().maxAge("cc_csrf", 0));

        Integer sessions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'student-user'",
                Integer.class);
        String storedRefresh = jdbc.queryForObject(
                "SELECT \"refreshToken\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'student-user'",
                String.class);

        org.junit.jupiter.api.Assertions.assertEquals(0, sessions);
        org.junit.jupiter.api.Assertions.assertNull(storedRefresh);
    }

    @Test
    void loginAcceptsMixedCaseAndWhitespaceEmailsLikeRegistration() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "jest-java-login-mixed-case")
                        .content("""
                                {"email":"  Student@CampusCore.Edu ","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("student-user"))
                .andExpect(jsonPath("$.user.email").value("student@campuscore.edu"));

        Integer failedAttempts = jdbc.queryForObject(
                "SELECT \"failedLoginAttempts\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'student-user'",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, failedAttempts);
    }

    @Test
    void loginRejectsInvalidCredentialsAndLocksAfterTheFifthFailure() throws Exception {
        insertStudentUser("locked-user", "locked@campuscore.edu", "password123", 4, null);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"locked@campuscore.edu","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        Integer failedAttempts = jdbc.queryForObject(
                "SELECT \"failedLoginAttempts\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'locked-user'",
                Integer.class);
        Object lockedUntil = jdbc.queryForObject(
                "SELECT \"lockedUntil\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'locked-user'",
                Object.class);
        Integer sessions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'locked-user'",
                Integer.class);

        org.junit.jupiter.api.Assertions.assertEquals(5, failedAttempts);
        org.junit.jupiter.api.Assertions.assertNotNull(lockedUntil);
        org.junit.jupiter.api.Assertions.assertEquals(0, sessions);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"locked@campuscore.edu","password":"password123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Authentication")));
    }

    private void insertStudentUser(
            String userId,
            String email,
            String password,
            int failedAttempts,
            Instant lockedUntil) {
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\""
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\","
                        + " \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"lockedUntil\","
                        + " \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                userId,
                email,
                passwordEncoder.encode(password),
                "Student",
                "One",
                "ACTIVE",
                true,
                false,
                failedAttempts,
                localDateTime(lockedUntil),
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

    private org.springframework.test.web.servlet.ResultActions loginStudent() throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "jest-java-login")
                .content("""
                        {"email":"student@campuscore.edu","password":"password123"}
                        """));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
