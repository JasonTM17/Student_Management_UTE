package io.campuscore.restfulapi.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract: with the mail subsystem disabled (mail.enabled=false) the OTP
 * paths must fail loudly with 502 MAIL_DELIVERY_FAILED instead of silently
 * succeeding and leaving the user without a deliverable code.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:two_factor_mail;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "mail.enabled=false"
})
class TwoFactorMailDisabledDeliveryTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private io.campuscore.restfulapi.mail.service.EmailService emailService;

    @BeforeEach
    void prepareMailDisabledFixture() {
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
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\""
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\","
                        + " \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"lockedUntil\","
                        + " \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "student-user",
                "student@campuscore.edu",
                passwordEncoder.encode("password123"),
                "Student",
                "One",
                "ACTIVE",
                true,
                false,
                0,
                null,
                LocalDateTime.ofInstant(BASE_TIME, ZoneOffset.UTC),
                LocalDateTime.ofInstant(BASE_TIME, ZoneOffset.UTC));
    }

    @Test
    void enableFailsWith502WhenMailIsDisabled() throws Exception {
        mvc.perform(post("/api/v1/me/two-factor/enable")
                        .with(jwt().jwt(token -> token.subject("student-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MAIL_DELIVERY_FAILED"));

        Integer challenges = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"TwoFactorChallenge\"",
                Integer.class);
        assertEquals(0, challenges, "a failed send must not leave a usable challenge row");
        verify(emailService, never()).sendHtmlEmail(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void loginSecondFactorFailsWith502WhenMailIsDisabled() throws Exception {
        jdbc.update(
                "UPDATE \"campuscore_auth\".\"User\" SET \"twoFactorEnabled\" = TRUE WHERE \"id\" = 'student-user'");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@campuscore.edu\",\"password\":\"password123\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MAIL_DELIVERY_FAILED"));

        Integer challenges = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"TwoFactorChallenge\"",
                Integer.class);
        assertEquals(0, challenges);

        Integer sessions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\"",
                Integer.class);
        assertEquals(0, sessions, "no tokens may be issued when the code cannot be delivered");
    }
}
