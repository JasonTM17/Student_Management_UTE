package io.campuscore.restfulapi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * SEC-P0-1: every seeded demo account (README-documented credentials plus the
 * whole @campuscore.demo population) is LOCKED once V48 applies, so a
 * Flyway-initialized deployment never ships working documented logins by
 * default. V82 subsequently re-activates exactly the runbook-documented .demo
 * subset (Four-Eyes approver + council examiners), and DemoAccountGate decides
 * per environment whether that subset ends up ACTIVE (demo hosts) or is
 * re-locked on every boot (public hosts, {@code DEMO_ACCOUNTS_ENABLED=false});
 * {@code DemoAccountGateRunbookParityTest} pins that list parity.
 * Real accounts issued later with arbitrary emails must stay ACTIVE.
 */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:seed_lock;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class SeededAccountLockMigrationTest {

    private static final String DEMO_PATTERN_CLAUSE = "\"email\" LIKE '%@campuscore.demo'";
    private static final String DEMO_LIST_CLAUSE =
            "\"email\" IN ('student@campuscore.edu', 'lecturer@campuscore.edu', 'admin@campuscore.edu')";

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * This is the only test in the auth package that clears the shared User
     * table, and AuthRuntimeConfigurationTest depends on the migration-seeded
     * demo student surviving between classes. Re-seed the canonical demo rows
     * afterwards so class order can never strand that test with an empty
     * table (the CI failure this guard prevents: EmptyResultDataAccess in
     * AuthRuntimeConfigurationTest).
     */
    @AfterEach
    void restoreMigrationSeed() {
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\""
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\","
                        + "  \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\")"
                        + " SELECT 'student-user', 'student@campuscore.edu',"
                        + " '$2a$10$raV9MB3Qmj1Rbu2Rmo1vNup7VsC2OM3AqmcTcTLzbNyMyI4r2rJBe', 'Demo', 'Student',"
                        + " 'LOCKED', TRUE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP"
                        + " WHERE NOT EXISTS (SELECT 1 FROM \"campuscore_auth\".\"User\" WHERE \"email\" = 'student@campuscore.edu')");
    }

    @Test
    void v48AndItsH2TwinCarryTheSameCompleteLockPredicate() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V48__lock_seeded_demo_accounts.sql");
        assertThat(migration).exists();
        String sql = Files.readString(migration);
        assertThat(sql).contains("'LOCKED'");
        assertThat(sql).contains(DEMO_PATTERN_CLAUSE);
        assertThat(sql).contains(DEMO_LIST_CLAUSE);

        Path h2Twin = Path.of("src/test/resources/db/migration-h2/V15__lock_seeded_demo_accounts.sql");
        assertThat(h2Twin).exists();
        String twin = Files.readString(h2Twin);
        assertThat(twin).contains(DEMO_PATTERN_CLAUSE);
        assertThat(twin).contains(DEMO_LIST_CLAUSE);
    }

    @Test
    void theLockPredicateSparesRealAccountsAndCoversEverySeedPopulation() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."User" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "email" VARCHAR(320) NOT NULL,
                    "status" VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
                    "updatedAt" TIMESTAMP
                )
                """);
        jdbc.update("DELETE FROM \"campuscore_auth\".\"User\"");
        // The persistence context may have created the entity-backed table
        // (several NOT NULL columns), so satisfy every required column.
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\""
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\","
                        + "  \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\")"
                        + " SELECT 'u1', 'student@campuscore.edu', 'x', 'Demo', 'Student', 'ACTIVE', TRUE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP"
                        + " UNION ALL SELECT 'u2', 'lecturer@campuscore.edu', 'x', 'Demo', 'Lecturer', 'ACTIVE', TRUE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP"
                        + " UNION ALL SELECT 'u3', 'admin@campuscore.edu', 'x', 'Demo', 'Admin', 'ACTIVE', TRUE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP"
                        + " UNION ALL SELECT 'u4', 'admin002@campuscore.demo', 'x', 'Demo', 'Admin', 'ACTIVE', TRUE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP"
                        + " UNION ALL SELECT 'u5', 'tran.thanh@campuscore.demo', 'x', 'Demo', 'Student', 'ACTIVE', TRUE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP"
                        + " UNION ALL SELECT 'u6', 'issued.student@example.edu.vn', 'x', 'Real', 'Student', 'ACTIVE', TRUE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP");

        // The exact statement V48 applies (parameterized here; the migration
        // file is pinned to the same predicate by the assertion above).
        int updated = jdbc.update(
                "UPDATE \"campuscore_auth\".\"User\" SET \"status\" = 'LOCKED', \"updatedAt\" = CURRENT_TIMESTAMP"
                        + " WHERE \"status\" = 'ACTIVE' AND (\"email\" LIKE ? OR \"email\" IN (?, ?, ?))",
                "%@campuscore.demo", "student@campuscore.edu", "lecturer@campuscore.edu", "admin@campuscore.edu");
        assertThat(updated).isEqualTo(5);

        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"User\" WHERE \"status\" = 'ACTIVE'",
                Integer.class);
        assertThat(active).isEqualTo(1);
        String realEmail = jdbc.queryForObject(
                "SELECT \"email\" FROM \"campuscore_auth\".\"User\" WHERE \"status\" = 'ACTIVE'",
                String.class);
        assertThat(realEmail).isEqualTo("issued.student@example.edu.vn");
    }
}
