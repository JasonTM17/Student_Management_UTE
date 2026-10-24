package io.campuscore.restfulapi.auth.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.campuscore.restfulapi.auth.repository.PasswordResetTokenRepository;
import io.campuscore.restfulapi.auth.job.PasswordResetTokenRetentionJob;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Contract of the reset-token retention sweep on H2: a consumed token or an
 * expired-unconsumed one leaves the table after the seven-day grace window,
 * while rows inside the window survive. Dropping them cannot resurrect a
 * link — the consume path already refuses used and expired rows.
 */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:password_reset_retention;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class PasswordResetTokenRetentionTest {

    @Autowired
    private PasswordResetTokenRepository tokens;

    @Autowired
    private PasswordResetTokenRetentionJob retentionJob;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepareTokenFixture() {
        // Flyway is disabled for this H2 context; the table mirrors the V114
        // DDL minus the user FK — the sweep never joins users.
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
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
        jdbc.update("DELETE FROM \"campuscore_auth\".\"PasswordResetToken\"");
    }

    @Test
    void purgeRemovesConsumedAndExpiredRowsPastTheGraceWindow() {
        Instant now = Instant.now();

        // Rows past the window: consumed eight days ago, expired unconsumed
        // eight days ago. Both must disappear.
        String consumedOld = tokens.insert(
                "retention-user-old", "hash-old", now.plus(Duration.ofHours(1)), null);
        tokens.consumeIfUnusedAndUnexpired("hash-old", now.minus(Duration.ofDays(8)));
        String expiredOld = tokens.insert(
                "retention-user-expired", "hash-expired", now.minus(Duration.ofDays(8)), null);

        // Rows inside the window: freshly issued, and consumed two days ago.
        // Both must survive the sweep.
        String fresh = tokens.insert(
                "retention-user-fresh", "hash-fresh", now.plus(Duration.ofMinutes(10)), null);
        String consumedRecent = tokens.insert(
                "retention-user-recent", "hash-recent", now.plus(Duration.ofHours(1)), null);
        tokens.consumeIfUnusedAndUnexpired("hash-recent", now.minus(Duration.ofDays(2)));

        retentionJob.purgeStaleTokens();

        assertTrue(tokens.findByTokenHash("hash-fresh").isPresent(),
                "a fresh token must survive retention");
        assertTrue(tokens.findByTokenHash("hash-recent").isPresent(),
                "a token consumed inside the grace window must survive retention");
        assertFalse(tokens.findByTokenHash("hash-old").isPresent(),
                "a token consumed past the grace window must be purged");
        assertFalse(tokens.findByTokenHash("hash-expired").isPresent(),
                "an expired unconsumed token past the grace window must be purged");
    }

    @Test
    void purgeStaleReportsTheNumberOfRemovedRows() {
        Instant now = Instant.now();
        tokens.insert("retention-count-user", "hash-count", now.plus(Duration.ofHours(1)), null);
        tokens.consumeIfUnusedAndUnexpired("hash-count", now.minus(Duration.ofDays(10)));

        int removed = tokens.purgeStale(now.minus(Duration.ofDays(7)));

        assertTrue(removed >= 1, "the sweep must report at least the row it removed");
        assertFalse(tokens.findByTokenHash("hash-count").isPresent());
        assertEquals(0, countTokens("retention-count-user"));
    }

    private int countTokens(String userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"PasswordResetToken\" WHERE \"userId\" = ?",
                Integer.class, userId);
        return count == null ? -1 : count;
    }
}
