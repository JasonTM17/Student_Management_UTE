package io.campuscore.restfulapi.auth;

import static io.campuscore.restfulapi.testsupport.CurrentAccountJwt.fixtureJwt;

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
        // Account mutation validates curriculum/department references and, on
        // role conversion, counts rows across the academic/thesis/engagement
        // history tables. This fixture runs without Flyway, so those schemas
        // get the minimal column set the service queries.
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS academic");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS thesis");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS engagement");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Curriculum\" (\"id\" VARCHAR(120) PRIMARY KEY, \"isActive\" BOOLEAN NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Department\" (\"id\" VARCHAR(120) PRIMARY KEY, \"isActive\" BOOLEAN NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Enrollment\" (\"id\" VARCHAR(120) PRIMARY KEY, \"studentId\" VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Attendance\" (\"id\" VARCHAR(120) PRIMARY KEY, \"studentId\" VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"RegistrationSlip\" (\"id\" VARCHAR(120) PRIMARY KEY, \"studentId\" VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"CreditLimitApplication\" (\"id\" VARCHAR(120) PRIMARY KEY, \"studentId\" VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.conduct_semester_score (id VARCHAR(120) PRIMARY KEY, student_id VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.conduct_activity (id VARCHAR(120) PRIMARY KEY, student_id VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"EnrollmentEvent\" (\"id\" VARCHAR(120) PRIMARY KEY, \"studentId\" VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Section\" (\"id\" VARCHAR(120) PRIMARY KEY, \"lecturerId\" VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS thesis.thesis_group_member (id VARCHAR(120) PRIMARY KEY, student_id VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS thesis.thesis_group (id VARCHAR(120) PRIMARY KEY, leader_student_id VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS thesis.thesis_group_report (id VARCHAR(120) PRIMARY KEY, submitted_by VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS thesis.thesis_council_member (id VARCHAR(120) PRIMARY KEY, lecturer_id VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS thesis.thesis_topic_supervisor (id VARCHAR(120) PRIMARY KEY, lecturer_id VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS thesis.thesis_topic_score (id VARCHAR(120) PRIMARY KEY, lecturer_id VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS thesis.thesis_topic (id VARCHAR(120) PRIMARY KEY, gvpb_lecturer_id VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS engagement.\"Announcement\" (\"id\" VARCHAR(120) PRIMARY KEY, \"lecturerId\" VARCHAR(120))");
        jdbc.update("DELETE FROM academic.\"Curriculum\"");
        jdbc.update("DELETE FROM academic.\"Department\"");
        jdbc.update(
                "INSERT INTO academic.\"Curriculum\" (\"id\", \"isActive\") VALUES ('curriculum-demo', TRUE)");
        jdbc.update(
                "INSERT INTO academic.\"Department\" (\"id\", \"isActive\") VALUES ('department-demo', TRUE)");
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
                        .with(fixtureJwt(jdbc, "admin-user", token -> token.subject("admin-user").claim("roles", java.util.List.of("ADMIN")), "ADMIN")
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

        // Credential replacement revokes both transport forms of the earlier access token.
        mvc.perform(put("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0900000000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));
        mvc.perform(get("/api/v1/auth/me").cookie(login.getResponse().getCookie("cc_access_token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));

        MvcResult freshLogin = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@campuscore.edu\",\"password\":\"" + rotatedSecret + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andReturn();
        String freshAccessToken = objectMapper.readTree(freshLogin.getResponse().getContentAsString())
                .get("accessToken").asText();
        mvc.perform(put("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + freshAccessToken)
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
                .andExpect(jsonPath("$.code").value("INVALID_OLD_PASSWORD"))
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));

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
    void tempCredentialRotationAuditsSeparatelyFromOrdinaryPasswordChange() throws Exception {
        // An office-issued credential that gets rotated is the audit trail's
        // only signal that an admin-issued temporary password reached its
        // owner (or a hijacker) — it must not read as an ordinary change.
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
                .andReturn();
        String accessToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();

        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + issuedSecret
                                + "\",\"newPassword\":\"" + rotatedSecret + "\"}"))
                .andExpect(status().isOk());

        assertAuditAction("TEMP_CREDENTIAL_ROTATED",
                "User rotated an office-issued temporary password; all sessions were revoked");

        // The ordinary self-rotation path keeps its original marker.
        String secondSecret = "second-" + UUID.randomUUID();
        MvcResult relogin = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@campuscore.edu\",\"password\":\"" + rotatedSecret + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String secondToken = objectMapper.readTree(relogin.getResponse().getContentAsString())
                .get("accessToken").asText();

        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + secondToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + rotatedSecret
                                + "\",\"newPassword\":\"" + secondSecret + "\"}"))
                .andExpect(status().isOk());

        assertAuditAction("PASSWORD_CHANGED",
                "User rotated their own password; all sessions were revoked");
    }

    @Test
    void changePasswordAppliesThePasswordPolicyBeforeRotating() throws Exception {
        // The stored credential ("password123") is not policy-checked — only
        // new candidates are. Each rejection is a stable 400 code the form
        // can render against.
        String oldSecret = "password123";
        String newSecret = "new-" + UUID.randomUUID();
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@campuscore.edu\",\"password\":\"" + oldSecret + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Sub-8 candidates never reach the policy: bean validation refuses
        // them at the DTO boundary with the shared validation code.
        assertPolicyRejection(accessToken, oldSecret, "s1", "VALIDATION_ERROR");
        assertPolicyRejection(accessToken, oldSecret, "1234567890", "PASSWORD_TOO_WEAK");
        // Local-part of the account email (>= 4 chars) inside the candidate.
        assertPolicyRejection(accessToken, oldSecret, "xStudent9", "PASSWORD_CONTAINS_IDENTITY");
        // 70 ASCII chars + 'đ' (2 UTF-8 bytes) + '1' = 73 bytes > the
        // 72-byte BCrypt ceiling — rejected, never silently truncated.
        assertPolicyRejection(accessToken, oldSecret, "a".repeat(70) + "đ1", "PASSWORD_TOO_LONG");
        // A denylisted candidate loses to its own code even when it also
        // happens to equal the current password.
        assertPolicyRejection(accessToken, oldSecret, "password123", "PASSWORD_TOO_COMMON");

        // A compliant candidate rotates normally …
        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + oldSecret
                                + "\",\"newPassword\":\"" + newSecret + "\"}"))
                .andExpect(status().isOk());

        // Re-authenticate after rotation before testing reuse of the current password.
        MvcResult freshLogin = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@campuscore.edu\",\"password\":\"" + newSecret + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String freshAccessToken = objectMapper.readTree(freshLogin.getResponse().getContentAsString())
                .get("accessToken").asText();
        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + freshAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + newSecret
                                + "\",\"newPassword\":\"" + newSecret + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_REUSED"));
    }

    @Test
    void ordinaryPasswordRotationRevokesBothAccessTransportsAndAdvancesEpochOnEachReplacement() throws Exception {
        String secret = "password123";
        MvcResult login = loginStudent().andExpect(status().isOk()).andReturn();
        for (long expectedVersion = 1; expectedVersion <= 2; expectedVersion++) {
            String access = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
            String replacement = "epoch-" + UUID.randomUUID();
            mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + access)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oldPassword\":\"" + secret + "\",\"newPassword\":\"" + replacement + "\"}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_INVALID"));
            mvc.perform(get("/api/v1/auth/me").cookie(login.getResponse().getCookie("cc_access_token")))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_INVALID"));
            org.junit.jupiter.api.Assertions.assertEquals(expectedVersion, jdbc.queryForObject(
                    "SELECT \"credentialVersion\" FROM campuscore_auth.\"User\" WHERE \"id\"='student-user'", Long.class));
            login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"student@campuscore.edu\",\"password\":\"" + replacement + "\"}"))
                    .andExpect(status().isOk()).andReturn();
            String fresh = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
            org.junit.jupiter.api.Assertions.assertEquals(expectedVersion,
                    ((Number) jwtDecoder.decode(fresh).getClaim("credentialVersion")).longValue());
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + fresh)).andExpect(status().isOk());
            secret = replacement;
        }
    }

    @Test
    void reconciledDemoRoleMigrationsRevokePreexistingAccessAndRefreshSessions() throws Exception {
        String user = "admin-user-002";
        String initialSecret = "PreMigration-" + UUID.randomUUID();
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.ensureUser(jdbc, user);
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.ensureCredentialReplacementSchema(jdbc);
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, user, "ADMIN");
        jdbc.update("UPDATE campuscore_auth.\"User\""
                + " SET \"email\"='admin002@campuscore.demo', \"password\"=? WHERE \"id\"=?",
                passwordEncoder.encode(initialSecret), user);
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Lecturer\""
                + " (\"userId\" VARCHAR(120), \"isActive\" BOOLEAN DEFAULT TRUE, \"updatedAt\" TIMESTAMP)");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.pendingLoginChallenge(
                jdbc, "challenge-pre-v125", user);

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin002@campuscore.demo\",\"password\":\"" + initialSecret + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        String oldAccess = body.get("accessToken").asText();
        String oldRefresh = body.get("refreshToken").asText();

        try (var connection = jdbc.getDataSource().getConnection()) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
                    connection,
                    new org.springframework.core.io.ClassPathResource(
                            "db/migration/V124__reconcile_demo_role_accounts_and_hygiene.sql"));
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
                    connection,
                    new org.springframework.core.io.ClassPathResource(
                            "db/migration/V125__revoke_reconciled_demo_sessions.sql"));
        }

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + oldAccess))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"TwoFactorChallenge\""
                        + " WHERE \"userId\"=? AND \"purpose\"='LOGIN' AND \"consumedAt\" IS NULL",
                Integer.class, user));
    }

    private void assertPolicyRejection(String accessToken, String oldSecret, String candidate, String code)
            throws Exception {
        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + oldSecret
                                + "\",\"newPassword\":\"" + candidate + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
    }

    private void assertAuditAction(String action, String summary) {
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\""
                        + " WHERE \"entityId\" = 'student-user' AND \"action\" = ? AND \"summary\" = ?",
                Integer.class,
                action, summary));
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

    @Test
    void lockedAccountWithLiveSessionFromSameIpCanStillLogin() throws Exception {
        Instant now = Instant.now();
        insertStudentUser("trusted-ip-user", "trusted@campuscore.edu", "password123", 5,
                now.plusSeconds(3600));
        insertSession("session-trusted", "trusted-ip-user", "203.0.113.10", now.plusSeconds(7200));

        mvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trusted@campuscore.edu","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
    }

    @Test
    void lockedAccountWithSessionFromDifferentIpStaysLocked() throws Exception {
        Instant now = Instant.now();
        insertStudentUser("foreign-ip-user", "foreign@campuscore.edu", "password123", 5,
                now.plusSeconds(3600));
        insertSession("session-foreign", "foreign-ip-user", "198.51.100.7", now.plusSeconds(7200));

        mvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.99");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"foreign@campuscore.edu","password":"password123"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lockedAccountWithExpiredSessionFromSameIpStaysLocked() throws Exception {
        Instant now = Instant.now();
        insertStudentUser("expired-ip-user", "expired@campuscore.edu", "password123", 5,
                now.plusSeconds(3600));
        insertSession("session-expired", "expired-ip-user", "203.0.113.10", now.minusSeconds(60));

        mvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"expired@campuscore.edu","password":"password123"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private void insertSession(String sessionId, String userId, String ipAddress, Instant expiresAt) {
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"Session\""
                        + " (\"id\", \"userId\", \"refreshToken\", \"userAgent\", \"ipAddress\","
                        + " \"expiresAt\", \"createdAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                sessionId,
                userId,
                "refresh-" + sessionId,
                "test-agent",
                ipAddress,
                localDateTime(expiresAt),
                localDateTime(BASE_TIME));
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
