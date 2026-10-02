package io.campuscore.restfulapi.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import io.campuscore.restfulapi.auth.repository.TwoFactorChallengeRepository;
import io.campuscore.restfulapi.auth.service.TwoFactorChallengeRetentionJob;

/**
 * Contract of the challenge retention sweep on H2: a consumed challenge or an
 * expired-unconsumed one leaves the table after the grace window, while rows
 * inside the window — fresh or recently consumed — survive. Without the sweep
 * the challenge table grew without bound (one row per 2FA login forever).
 */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:two_factor_retention;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class TwoFactorChallengeRetentionTest {

    @Autowired
    private TwoFactorChallengeRepository challenges;

    @Autowired
    private TwoFactorChallengeRetentionJob retentionJob;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepareChallengeFixture() {
        // Same standalone fixture the login-persistence test uses: Flyway is
        // disabled for this H2 context, so the schema and table are created
        // here (shape mirrors the H2 twin of V85, minus the user FK — the
        // sweep never joins users).
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."TwoFactorChallenge" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "purpose" VARCHAR(16) NOT NULL CHECK ("purpose" IN ('LOGIN', 'ENABLE', 'DISABLE')),
                    "codeHash" VARCHAR(64) NOT NULL,
                    "expiresAt" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "consumedAt" TIMESTAMP WITH TIME ZONE,
                    "attempts" INTEGER NOT NULL DEFAULT 0,
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    @Test
    void purgeRemovesConsumedAndExpiredRowsPastTheGraceWindow() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofDays(1));

        // Rows past the window: consumed two days ago, expired unconsumed two
        // days ago. Both must disappear.
        String consumedOld = challenges.insert("retention-user-old", "LOGIN", "hash-old", now.plus(Duration.ofHours(1)));
        challenges.consume(consumedOld, now.minus(Duration.ofDays(2)));
        String expiredOld = challenges.insert("retention-user-expired", "LOGIN", "hash-expired", now.minus(Duration.ofDays(2)));

        // Rows inside the window: freshly issued, and consumed twelve hours
        // ago. Both must survive the sweep.
        String fresh = challenges.insert("retention-user-fresh", "LOGIN", "hash-fresh", now.plus(Duration.ofMinutes(10)));
        String consumedRecent = challenges.insert("retention-user-recent", "LOGIN", "hash-recent", now.plus(Duration.ofHours(1)));
        challenges.consume(consumedRecent, now.minus(Duration.ofHours(12)));

        retentionJob.purgeStaleChallenges();

        assertTrue(challenges.findById(fresh).isPresent(), "a fresh challenge must survive retention");
        assertTrue(challenges.findById(consumedRecent).isPresent(),
                "a challenge consumed inside the grace window must survive retention");
        assertFalse(challenges.findById(consumedOld).isPresent(),
                "a challenge consumed past the grace window must be purged");
        assertFalse(challenges.findById(expiredOld).isPresent(),
                "an expired unconsumed challenge past the grace window must be purged");
    }

    @Test
    void purgeStaleReportsTheNumberOfRemovedRows() {
        Instant now = Instant.now();
        String consumedOld = challenges.insert("retention-count-user", "ENABLE", "hash-count", now.plus(Duration.ofHours(1)));
        challenges.consume(consumedOld, now.minus(Duration.ofDays(3)));

        int removed = challenges.purgeStale(now.minus(Duration.ofDays(1)));

        assertTrue(removed >= 1, "the sweep must report at least the row it removed");
        assertFalse(challenges.findById(consumedOld).isPresent());
        assertEquals(0, countChallenges("retention-count-user"));
    }

    private int countChallenges(String userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"TwoFactorChallenge\" WHERE \"userId\" = ?",
                Integer.class, userId);
        return count == null ? -1 : count;
    }
}
