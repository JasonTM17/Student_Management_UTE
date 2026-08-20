package io.campuscore.restfulapi.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "migration.thesis-read.enabled=false",
        "migration.auth-login.enabled=true",
        "spring.flyway.enabled=false"
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
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"auth\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "auth"."User" (
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
                CREATE TABLE IF NOT EXISTS "auth"."Role" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(80) UNIQUE NOT NULL,
                    "description" VARCHAR(500),
                    "isSystem" BOOLEAN NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "auth"."Permission" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(160) UNIQUE NOT NULL,
                    "description" VARCHAR(500),
                    "module" VARCHAR(80) NOT NULL,
                    "action" VARCHAR(80) NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "auth"."UserRole" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "roleId" VARCHAR(120) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "auth"."RolePermission" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "roleId" VARCHAR(120) NOT NULL,
                    "permissionId" VARCHAR(120) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "auth"."Student" (
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
                CREATE TABLE IF NOT EXISTS "auth"."Lecturer" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) UNIQUE NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL,
                    "employeeId" VARCHAR(120) UNIQUE NOT NULL,
                    "isActive" BOOLEAN NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "auth"."Session" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "refreshToken" VARCHAR(200) NOT NULL,
                    "userAgent" VARCHAR(500),
                    "ipAddress" VARCHAR(80),
                    "expiresAt" TIMESTAMP NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.update("DELETE FROM \"auth\".\"Session\"");
        jdbc.update("DELETE FROM \"auth\".\"Student\"");
        jdbc.update("DELETE FROM \"auth\".\"Lecturer\"");
        jdbc.update("DELETE FROM \"auth\".\"RolePermission\"");
        jdbc.update("DELETE FROM \"auth\".\"UserRole\"");
        jdbc.update("DELETE FROM \"auth\".\"Permission\"");
        jdbc.update("DELETE FROM \"auth\".\"Role\"");
        jdbc.update("DELETE FROM \"auth\".\"User\"");
        insertStudentUser("student-user", "student@campuscore.edu", "password123", 0, null);
    }

    @Test
    void loginIssuesBodyTokensCookiesAndPersistsHashedRefreshSession() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "jest-java-login")
                        .content("""
                                {"email":"student@campuscore.edu","password":"password123"}
                                """))
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
                "SELECT \"failedLoginAttempts\" FROM \"auth\".\"User\" WHERE \"id\" = 'student-user'",
                Integer.class);
        Integer sessions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"auth\".\"Session\" WHERE \"userId\" = 'student-user'",
                Integer.class);
        String storedRefresh = jdbc.queryForObject(
                "SELECT \"refreshToken\" FROM \"auth\".\"Session\" WHERE \"userId\" = 'student-user'",
                String.class);

        org.junit.jupiter.api.Assertions.assertEquals(0, failedAttempts);
        org.junit.jupiter.api.Assertions.assertEquals(1, sessions);
        org.junit.jupiter.api.Assertions.assertEquals(64, storedRefresh.length());
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
                "SELECT \"failedLoginAttempts\" FROM \"auth\".\"User\" WHERE \"id\" = 'locked-user'",
                Integer.class);
        Object lockedUntil = jdbc.queryForObject(
                "SELECT \"lockedUntil\" FROM \"auth\".\"User\" WHERE \"id\" = 'locked-user'",
                Object.class);
        Integer sessions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"auth\".\"Session\" WHERE \"userId\" = 'locked-user'",
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
                "INSERT INTO \"auth\".\"User\""
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
                "SELECT COUNT(*) FROM \"auth\".\"Role\" WHERE \"id\" = 'role-student'",
                Integer.class);
        if (roleCount == 0) {
            jdbc.update(
                "INSERT INTO \"auth\".\"Role\""
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
                "INSERT INTO \"auth\".\"Permission\""
                        + " (\"id\", \"name\", \"description\", \"module\", \"action\", \"createdAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                "permission-" + userId,
                "thesis.read." + userId,
                null,
                "thesis",
                "read",
                localDateTime(BASE_TIME));
        jdbc.update("INSERT INTO \"auth\".\"UserRole\" (\"id\", \"userId\", \"roleId\") VALUES (?, ?, ?)",
                "user-role-" + userId, userId, "role-student");
        jdbc.update("INSERT INTO \"auth\".\"RolePermission\" (\"id\", \"roleId\", \"permissionId\") VALUES (?, ?, ?)",
                "role-permission-" + userId, "role-student", "permission-" + userId);
        jdbc.update(
                "INSERT INTO \"auth\".\"Student\""
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
