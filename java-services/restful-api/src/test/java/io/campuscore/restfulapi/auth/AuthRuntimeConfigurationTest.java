package io.campuscore.restfulapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.auth.service.AuthLoginService;
import io.campuscore.restfulapi.auth.web.AuthLoginController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
class AuthRuntimeConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void authSessionContractIsAvailableWithoutLegacyFeatureFlag() {
        assertThat(context.getBean(AuthLoginService.class)).isNotNull();
        assertThat(context.getBean(AuthLoginController.class)).isNotNull();
    }

    /**
     * V15 (twin of production V48) locks every seeded demo account because its
     * credentials are published in the README. On a freshly migrated database
     * the documented password must therefore be refused; the local demo
     * unlocks the account with explicit SQL — exactly what the CI compose job
     * and the README "Local demo accounts" section describe — and only then
     * starts a session.
     *
     * <p>Several persistence tests legitimately wipe the shared H2 User table
     * and class execution order is not guaranteed across platforms, so this
     * test re-establishes the migration-seeded demo student idempotently
     * instead of assuming the Flyway seed survived.
     */
    @Test
    void migratedDemoStudentIsLockedUntilExplicitlyUnlocked() throws Exception {
        jdbc.update("""
                INSERT INTO "campuscore_auth"."User"
                 ("id", "email", "password", "firstName", "lastName", "status",
                  "emailVerified", "isSuperAdmin", "failedLoginAttempts", "createdAt", "updatedAt")
                SELECT 'student-user', 'student@campuscore.edu',
                 '$2a$10$raV9MB3Qmj1Rbu2Rmo1vNup7VsC2OM3AqmcTcTLzbNyMyI4r2rJBe', 'Demo', 'Student',
                 'LOCKED', TRUE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                WHERE NOT EXISTS (
                 SELECT 1 FROM "campuscore_auth"."User" WHERE "email" = 'student@campuscore.edu')
                """);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@campuscore.edu\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());

        String status = jdbc.queryForObject(
                "SELECT \"status\" FROM campuscore_auth.\"User\" WHERE \"email\" = 'student@campuscore.edu'",
                String.class);
        assertThat(status).isEqualTo("LOCKED");

        jdbc.update(
                "UPDATE campuscore_auth.\"User\" SET \"status\" = 'ACTIVE' WHERE \"email\" = 'student@campuscore.edu'");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@campuscore.edu\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("student@campuscore.edu"))
                .andExpect(jsonPath("$.user.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString());
    }
}
