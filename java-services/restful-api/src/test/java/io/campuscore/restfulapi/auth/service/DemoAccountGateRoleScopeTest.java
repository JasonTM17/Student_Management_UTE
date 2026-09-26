package io.campuscore.restfulapi.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Role-scoped behavior of the demo gate (audit finding S2): with
 * {@code app.demo.roles} set and the gate enabled, only demo accounts holding
 * an in-scope role become ACTIVE while the remaining demo accounts are LOCKED.
 * An unset scope keeps the historical all-ACTIVE behavior and a disabled gate
 * locks everyone regardless of scope. Non-demo accounts are never touched.
 *
 * <p>The gate is constructed manually per case so one H2 context covers the
 * whole behavior matrix without property-driven context restarts.
 */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:demo_gate_roles;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class DemoAccountGateRoleScopeTest {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepareFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."User" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "email" VARCHAR(200) NOT NULL,
                    "firstName" VARCHAR(120) NOT NULL,
                    "lastName" VARCHAR(120) NOT NULL,
                    "status" VARCHAR(40) NOT NULL DEFAULT 'LOCKED',
                    "failedLoginAttempts" INTEGER NOT NULL DEFAULT 0,
                    "lockedUntil" TIMESTAMP,
                    "updatedAt" TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."Role" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(80) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."UserRole" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "roleId" VARCHAR(120) NOT NULL
                )
                """);
        jdbc.update("DELETE FROM \"campuscore_auth\".\"UserRole\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Role\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"User\"");

        jdbc.update("INSERT INTO \"campuscore_auth\".\"Role\" (\"id\", \"name\") VALUES ('role-student', 'STUDENT')");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Role\" (\"id\", \"name\") VALUES ('role-lecturer', 'LECTURER')");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Role\" (\"id\", \"name\") VALUES ('role-admin', 'ADMIN')");
        seedDemoUser("demo-student", "student@campuscore.edu", List.of("role-student"));
        seedDemoUser("demo-lecturer", "lecturer@campuscore.edu", List.of("role-lecturer"));
        seedDemoUser("demo-admin", "admin@campuscore.edu", List.of("role-admin"));
        seedDemoUser("demo-council", "lecturer003@campuscore.demo", List.of("role-lecturer"));
        // A real account that shares nothing with the demo list: the gate must
        // never flip its status no matter how the scope is configured.
        seedDemoUser("real-user", "nguyen.van.a@student.hcmute.edu.vn", List.of("role-student"));
    }

    private void seedDemoUser(String id, String email, List<String> roleIds) {
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\""
                        + " (\"id\", \"email\", \"firstName\", \"lastName\", \"status\", \"failedLoginAttempts\")"
                        + " VALUES (?, ?, ?, ?, 'LOCKED', 3)",
                id, email, "Demo", "User");
        for (String roleId : roleIds) {
            jdbc.update(
                    "INSERT INTO \"campuscore_auth\".\"UserRole\" (\"id\", \"userId\", \"roleId\") VALUES (?, ?, ?)",
                    id + "-" + roleId, id, roleId);
        }
    }

    private DemoAccountGate gate(boolean enabled, String roles) {
        return new DemoAccountGate(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc.getDataSource()), enabled, roles);
    }

    private String statusOf(String email) {
        return jdbc.queryForObject(
                "SELECT \"status\" FROM \"campuscore_auth\".\"User\" WHERE \"email\" = ?",
                String.class, email);
    }

    @Test
    void studentScopeActivatesOnlyStudentDemoAccounts() {
        gate(true, "STUDENT").run(null);

        assertThat(statusOf("student@campuscore.edu")).isEqualTo("ACTIVE");
        assertThat(statusOf("lecturer@campuscore.edu")).isEqualTo("LOCKED");
        assertThat(statusOf("admin@campuscore.edu")).isEqualTo("LOCKED");
        assertThat(statusOf("lecturer003@campuscore.demo")).isEqualTo("LOCKED");
        // Never touch a real account.
        assertThat(statusOf("nguyen.van.a@student.hcmute.edu.vn")).isEqualTo("LOCKED");
    }

    @Test
    void multiRoleScopeActivatesEveryCoveredDemoRole() {
        gate(true, "student, lecturer").run(null);

        assertThat(statusOf("student@campuscore.edu")).isEqualTo("ACTIVE");
        assertThat(statusOf("lecturer@campuscore.edu")).isEqualTo("ACTIVE");
        assertThat(statusOf("lecturer003@campuscore.demo")).isEqualTo("ACTIVE");
        assertThat(statusOf("admin@campuscore.edu")).isEqualTo("LOCKED");
    }

    @Test
    void unsetScopeKeepsTheHistoricalAllActiveBehavior() {
        gate(true, "").run(null);

        assertThat(statusOf("student@campuscore.edu")).isEqualTo("ACTIVE");
        assertThat(statusOf("lecturer@campuscore.edu")).isEqualTo("ACTIVE");
        assertThat(statusOf("admin@campuscore.edu")).isEqualTo("ACTIVE");
        assertThat(statusOf("lecturer003@campuscore.demo")).isEqualTo("ACTIVE");
    }

    @Test
    void disabledGateLocksEveryDemoAccountRegardlessOfScope() {
        gate(false, "STUDENT").run(null);

        assertThat(statusOf("student@campuscore.edu")).isEqualTo("LOCKED");
        assertThat(statusOf("lecturer@campuscore.edu")).isEqualTo("LOCKED");
        assertThat(statusOf("admin@campuscore.edu")).isEqualTo("LOCKED");
        assertThat(statusOf("lecturer003@campuscore.demo")).isEqualTo("LOCKED");
    }

    @Test
    void demoAccountWithoutAnyRoleRowStaysLockedWhenScoped() {
        jdbc.update("DELETE FROM \"campuscore_auth\".\"UserRole\" WHERE \"userId\" = 'demo-admin'");

        gate(true, "STUDENT,ADMIN").run(null);

        assertThat(statusOf("student@campuscore.edu")).isEqualTo("ACTIVE");
        assertThat(statusOf("admin@campuscore.edu")).isEqualTo("LOCKED");
    }

    @Test
    void roleParsingIsCaseInsensitiveAndDropsBlankAndUnknownEntries() {
        assertThat(DemoAccountGate.parseRoles(null)).isEmpty();
        assertThat(DemoAccountGate.parseRoles("  ")).isEmpty();
        assertThat(DemoAccountGate.parseRoles("student, LECTURER ,")).isEqualTo(Set.of("STUDENT", "LECTURER"));
    }

    @Test
    void scopedActivationAlsoClearsLockoutCounters() {
        gate(true, "STUDENT").run(null);

        Integer attempts = jdbc.queryForObject(
                "SELECT \"failedLoginAttempts\" FROM \"campuscore_auth\".\"User\" WHERE \"email\" = ?",
                Integer.class, "student@campuscore.edu");
        assertThat(attempts).isZero();
        LocalDateTime updated = jdbc.queryForObject(
                "SELECT \"updatedAt\" FROM \"campuscore_auth\".\"User\" WHERE \"email\" = ?",
                LocalDateTime.class, "student@campuscore.edu");
        assertThat(updated).isNotNull();
    }
}
