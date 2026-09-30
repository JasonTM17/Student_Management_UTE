package io.campuscore.restfulapi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * V89 enforces the two role-ownership rules the product guarantees:
 *
 * <p>R1 — TRUONG_KHOA is a job title carried by a LECTURER account, never a
 * second hat on the academic-affairs office: V31 seeded the demo administrator
 * with both roles as a demo shortcut, so the sweep removes TRUONG_KHOA from
 * every account that also holds ADMIN or SUPER_ADMIN (exactly the production
 * finding behind this migration).
 *
 * <p>R2 — ADMIN is never assigned to a lecturer, a faculty head included.
 *
 * <p>The H2 harness stops before the academic lecturer registry, so the
 * database-level assertions here exercise the R1 sweep statement on the auth
 * tables the reduced chain does model; the PostgreSQL-only lecturer-profile
 * parts are pinned by file-content assertions, the same discipline
 * RegistrationFoundationMigrationTest uses for V14.
 */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:role_hygiene;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class RoleHygieneMigrationTest {

    /** The R1 sweep, copied verbatim from V89 / its H2 twin. */
    private static final String FACULTY_HEAD_OFFICE_SEPARATION = """
            DELETE FROM campuscore_auth."UserRole"
            WHERE "roleId" = (SELECT "id" FROM campuscore_auth."Role" WHERE "name" = 'TRUONG_KHOA')
              AND "userId" IN (
                  SELECT held."userId"
                  FROM campuscore_auth."UserRole" held
                  JOIN campuscore_auth."Role" office ON office."id" = held."roleId"
                  WHERE office."name" IN ('ADMIN', 'SUPER_ADMIN')
              )
            """;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v89SeparatesTheFacultyHeadRoleFromAdministrativeOfficeRoles() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V89__role_hygiene_faculty_head_separation.sql");
        assertThat(migration).exists();
        String sql = Files.readString(migration);
        // R1: the office-role sweep — both office roles, not just the seeded demo pair.
        assertThat(sql).contains("IN ('ADMIN', 'SUPER_ADMIN')");
        assertThat(sql).contains("'TRUONG_KHOA'");
        // R2: a lecturer may never hold ADMIN.
        assertThat(sql).contains("\"roleId\" = (SELECT \"id\" FROM campuscore_auth.\"Role\" WHERE \"name\" = 'ADMIN')");
        assertThat(sql).contains("FROM academic.\"Lecturer\"");
        // The faculty head is a job title on a lecturer, seeded with LECTURER +
        // the faculty-head role only — never ADMIN.
        assertThat(sql).contains("'Trưởng khoa'");
        assertThat(sql).contains("'Trưởng bộ môn'");
        assertThat(sql).contains("'role-lecturer'");
        assertThat(sql).doesNotContain("'role-admin'");
    }

    @Test
    void h2TwinCarriesTheSameOfficeRoleSweep() throws Exception {
        Path twin = Path.of(
                "src/test/resources/db/migration-h2/V89__role_hygiene_faculty_head_separation.sql");
        assertThat(twin).exists();
        String sql = Files.readString(twin);
        assertThat(sql).contains("IN ('ADMIN', 'SUPER_ADMIN')");
        assertThat(sql).contains("'TRUONG_KHOA'");
    }

    @Test
    void theSweepStripsFacultyHeadOnlyFromAccountsThatAlsoHoldAnOfficeRole() {
        seedUser("rh-admin-user", "rh-admin@campuscore.demo");
        seedUser("rh-faculty-user", "rh-faculty@campuscore.demo");
        grantRole("rh-ur-admin", "rh-admin-user", "role-admin");
        grantRole("rh-admin-user", "role-truong-khoa");
        grantRole("rh-faculty-user", "role-truong-khoa");

        jdbc.execute(FACULTY_HEAD_OFFICE_SEPARATION);

        // The academic-affairs account loses the faculty-head role...
        assertThat(holdsRole("rh-admin-user", "role-truong-khoa")).isFalse();
        assertThat(holdsRole("rh-admin-user", "role-admin")).isTrue();
        // ...while the lecturer who legitimately carries it keeps it, and a
        // second run of the same idempotent sweep changes nothing.
        assertThat(holdsRole("rh-faculty-user", "role-truong-khoa")).isTrue();

        jdbc.execute(FACULTY_HEAD_OFFICE_SEPARATION);
        assertThat(holdsRole("rh-faculty-user", "role-truong-khoa")).isTrue();
        assertThat(holdsRole("rh-admin-user", "role-truong-khoa")).isFalse();
    }

    private void seedUser(String id, String email) {
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\""
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\","
                        + "  \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\")"
                        + " SELECT ?, ?, 'x', 'Demo', 'Role Hygiene', 'ACTIVE', TRUE, FALSE, 0"
                        + " WHERE NOT EXISTS (SELECT 1 FROM \"campuscore_auth\".\"User\" WHERE \"id\" = ?)",
                id, email, id);
    }

    private void grantRole(String linkId, String userId, String roleId) {
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"UserRole\" (\"id\", \"userId\", \"roleId\")"
                        + " SELECT ?, ?, ? WHERE NOT EXISTS ("
                        + "   SELECT 1 FROM \"campuscore_auth\".\"UserRole\""
                        + "   WHERE \"userId\" = ? AND \"roleId\" = ?)",
                linkId, userId, roleId, userId, roleId);
    }

    private void grantRole(String userId, String roleId) {
        grantRole("rh-ur-" + userId + "-" + roleId, userId, roleId);
    }

    private boolean holdsRole(String userId, String roleId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"UserRole\""
                        + " WHERE \"userId\" = ? AND \"roleId\" = ?",
                Integer.class, userId, roleId);
        return count != null && count > 0;
    }
}
