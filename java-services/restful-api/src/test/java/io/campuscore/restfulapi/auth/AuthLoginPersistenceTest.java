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
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."AuthChallenge" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "purpose" VARCHAR(40) NOT NULL,
                    "tokenHash" VARCHAR(64) NOT NULL UNIQUE,
                    "expiresAt" TIMESTAMP NOT NULL,
                    "consumedAt" TIMESTAMP,
                    "attemptCount" INTEGER NOT NULL,
                    "lastSentAt" TIMESTAMP NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."AuthRateLimitBucket" (
                    "scope" VARCHAR(80) NOT NULL,
                    "bucketKeyHash" VARCHAR(64) NOT NULL,
                    "windowStart" TIMESTAMP NOT NULL,
                    "requestCount" INTEGER NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL,
                    PRIMARY KEY ("scope", "bucketKeyHash", "windowStart")
                )
                """);
        jdbc.update("DELETE FROM \"campuscore_auth\".\"AuthRateLimitBucket\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"AuthChallenge\"");
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
    void registrationCreatesAPendingStudentWithoutIssuingASession() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"new.student@campuscore.edu",
                                  "password":"password123",
                                  "firstName":"New",
                                  "lastName":"Student"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.email").value("new.student@campuscore.edu"))
                .andExpect(jsonPath("$.verificationRequired").value(true))
                .andExpect(jsonPath("$.expiresInSeconds").value(86400))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().doesNotExist("cc_access_token"))
                .andExpect(cookie().doesNotExist("cc_refresh_token"));

        String userId = jdbc.queryForObject(
                "SELECT \"id\" FROM \"campuscore_auth\".\"User\" WHERE \"email\" = ?",
                String.class,
                "new.student@campuscore.edu");
        Integer profiles = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Student\" WHERE \"userId\" = ?"
                        + " AND \"curriculumId\" = 'curriculum-demo' AND \"status\" = 'ACTIVE'",
                Integer.class,
                userId);
        String status = jdbc.queryForObject(
                "SELECT \"status\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = ?",
                String.class,
                userId);
        Boolean emailVerified = jdbc.queryForObject(
                "SELECT \"emailVerified\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = ?",
                Boolean.class,
                userId);
        Integer challenges = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"AuthChallenge\" WHERE \"userId\" = ?"
                        + " AND \"purpose\" = 'EMAIL_VERIFICATION' AND LENGTH(\"tokenHash\") = 64",
                Integer.class,
                userId);
        org.junit.jupiter.api.Assertions.assertEquals(1, profiles);
        org.junit.jupiter.api.Assertions.assertEquals("PENDING_VERIFICATION", status);
        org.junit.jupiter.api.Assertions.assertEquals(false, emailVerified);
        org.junit.jupiter.api.Assertions.assertEquals(1, challenges);
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
                                  "password":"password123",
                                  "firstName":"Managed",
                                  "lastName":"Student",
                                  "role":"STUDENT",
                                  "studentId":"SV-MANAGED-001"
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
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.lastName").value("Student"))
                .andExpect(jsonPath("$.phone").value("+84999999999"))
                .andExpect(jsonPath("$.address").value("Java monolith lane"))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"));

        mvc.perform(get("/api/v1/auth/me").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.lastName").value("Student"))
                .andExpect(jsonPath("$.phone").value("+84999999999"))
                .andExpect(jsonPath("$.address").value("Java monolith lane"));
    }

    @Test
    void changePasswordRequiresAuthenticationUpdatesHashAndRevokesRefreshSessions() throws Exception {
        MvcResult login = loginStudent().andReturn();
        JsonNode loginBody = objectMapper.readTree(login.getResponse().getContentAsString());
        String accessToken = loginBody.get("accessToken").asText();

        mvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"password123","newPassword":"newpass123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + accessToken)
                        .content("""
                                {"oldPassword":"wrong-password","newPassword":"newpass123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid old password"));

        mvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + accessToken)
                        .content("""
                                {"oldPassword":"password123","newPassword":"newpass123"}
                                """))
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

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"student@campuscore.edu","password":"password123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"student@campuscore.edu","password":"newpass123"}
                                """))
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
