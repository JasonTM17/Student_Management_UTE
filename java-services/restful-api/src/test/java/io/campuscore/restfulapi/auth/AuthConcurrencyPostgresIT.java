package io.campuscore.restfulapi.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.campuscore.restfulapi.auth.service.AuthChallengeTokenService;
import io.campuscore.restfulapi.auth.repository.AuthChallengeRepository.Purpose;
import io.campuscore.restfulapi.auth.service.AuthChallengeTokenService.IssuedChallengeToken;
import io.campuscore.restfulapi.auth.service.AuthLifecycleService;
import io.campuscore.restfulapi.auth.service.AuthLoginService;
import io.campuscore.restfulapi.auth.web.AuthDtos.EmailRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.ChallengeTokenRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.PasswordResetConfirmRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.RegisterRequest;
import io.campuscore.restfulapi.web.DomainException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

/**
 * PostgreSQL-only auth race regressions. The URL must point at an isolated
 * database supplied by the caller; no developer or hosted database is
 * selected implicitly.
 */
@EnabledIfEnvironmentVariable(named = "AUTH_POSTGRES_URL", matches = "jdbc:postgresql:.+")
@SpringBootTest(properties = {
        "deepseek.enabled=false",
        "mail.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "auth.lifecycle.resend-cooldown-seconds=0",
        "auth.lifecycle.max-requests-per-day=100"
})
@ActiveProfiles({"test", "persistence"})
class AuthConcurrencyPostgresIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthLifecycleService lifecycle;

    @Autowired
    private AuthLoginService login;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private final List<String> createdUserIds = new ArrayList<>();
    private final List<String[]> createdRateLimitKeys = new ArrayList<>();

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("AUTH_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("AUTH_POSTGRES_USER", "postgres"));
        registry.add("spring.datasource.password", () -> valueOr("AUTH_POSTGRES_PASSWORD", "postgres"));
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @AfterEach
    void cleanup() {
        for (String[] key : createdRateLimitKeys) {
            jdbc.update("DELETE FROM campuscore_auth.\"AuthRateLimitBucket\""
                            + " WHERE \"scope\" = ? AND \"bucketKeyHash\" = ?",
                    key[0], key[1]);
        }
        for (String userId : createdUserIds) {
            jdbc.update("DELETE FROM campuscore_auth.\"AuthChallenge\" WHERE \"userId\" = ?", userId);
            jdbc.update("DELETE FROM campuscore_auth.\"Session\" WHERE \"userId\" = ?", userId);
            jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE id = ?", userId);
        }
        createdUserIds.clear();
        createdRateLimitKeys.clear();
    }

    @Test
    void concurrentResendsLeaveExactlyOneCurrentChallenge() throws Exception {
        String userId = insertUser("PENDING_VERIFICATION");
        String email = userId + "@auth-race.invalid";
        rememberRateLimit("verification-resend:EMAIL", email);
        rememberRateLimit("verification-resend:IP", "10.0.0.1");
        rememberRateLimit("verification-resend:IP", "10.0.0.2");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> calls = List.of(
                    pool.submit(() -> resend(email, "10.0.0.1", ready, start)),
                    pool.submit(() -> resend(email, "10.0.0.2", ready, start)));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> call : calls) {
                call.get(20, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"AuthChallenge\""
                        + " WHERE \"userId\" = ? AND purpose = 'EMAIL_VERIFICATION' AND \"consumedAt\" IS NULL",
                Integer.class,
                userId));
    }

    @Test
    void concurrentWrongLoginsReachTheLockThresholdWithoutLostUpdates() throws Exception {
        String userId = insertUser("ACTIVE");
        String email = userId + "@auth-race.invalid";
        CountDownLatch ready = new CountDownLatch(5);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Future<?>> calls = new ArrayList<>();
            for (int index = 0; index < 5; index++) {
                String ip = "10.0.1." + (index + 1);
                calls.add(pool.submit(() -> wrongLogin(email, ip, ready, start)));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> call : calls) {
                call.get(20, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEquals(5, jdbc.queryForObject(
                "SELECT \"failedLoginAttempts\" FROM campuscore_auth.\"User\" WHERE id = ?",
                Integer.class,
                userId));
        assertNotNull(jdbc.queryForObject(
                "SELECT \"lockedUntil\" FROM campuscore_auth.\"User\" WHERE id = ?",
                Timestamp.class,
                userId));
    }

    @Test
    void concurrentRegistrationForTheSameEmailReturnsOnePendingAccountAndOneStableConflict() throws Exception {
        String email = "register-race-" + UUID.randomUUID() + "@auth-race.invalid";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<RegistrationAttempt>> calls = List.of(
                    pool.submit(() -> register(email, "10.0.2.1", ready, start)),
                    pool.submit(() -> register(email, "10.0.2.2", ready, start)));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<RegistrationAttempt> outcomes = new ArrayList<>();
            for (Future<RegistrationAttempt> call : calls) {
                outcomes.add(call.get(20, TimeUnit.SECONDS));
            }
            assertEquals(1, outcomes.stream().filter(RegistrationAttempt::accepted).count());
            assertEquals(1, outcomes.stream().filter(outcome -> "EMAIL_ALREADY_EXISTS".equals(outcome.code())).count());
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }

        List<String> userIds = jdbc.queryForList(
                "SELECT id FROM campuscore_auth.\"User\" WHERE email = ?",
                String.class,
                email);
        assertEquals(1, userIds.size());
        createdUserIds.add(userIds.getFirst());
    }

    @Test
    void passwordResetBlockedWhenDisableHoldsTheUserRowLock() throws Exception {
        String userId = insertUser("ACTIVE");
        String email = userId + "@auth-race.invalid";
        String oldHash = jdbc.queryForObject(
                "SELECT password FROM campuscore_auth.\"User\" WHERE id = ?",
                String.class,
                userId);
        IssuedChallengeToken issued = insertChallenge(userId, Purpose.PASSWORD_RESET);

        CountDownLatch disableLocked = new CountDownLatch(1);
        CountDownLatch allowDisableCommit = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> disable = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.update("UPDATE campuscore_auth.\"User\" SET status = 'DISABLED' WHERE id = ?", userId);
            disableLocked.countDown();
            await(allowDisableCommit);
            return null;
        }));
        assertTrue(disableLocked.await(10, TimeUnit.SECONDS));

        Future<String> reset = pool.submit(() -> {
            try {
                lifecycle.confirmPasswordReset(new PasswordResetConfirmRequest(issued.rawToken(), "disabled-race-123"));
                return "SUCCESS";
            } catch (DomainException exception) {
                return exception.code();
            }
        });
        // The reset transaction must wait on the user row until the disable
        // commits; releasing only after the waiter has been scheduled makes
        // the lock-order/race path deterministic without touching a shared DB.
        Thread.sleep(200);
        allowDisableCommit.countDown();
        assertEquals("AUTH_CHALLENGE_INVALID", reset.get(20, TimeUnit.SECONDS));
        disable.get(20, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertEquals("DISABLED", jdbc.queryForObject(
                "SELECT status FROM campuscore_auth.\"User\" WHERE id = ?", String.class, userId));
        assertEquals(oldHash, jdbc.queryForObject(
                "SELECT password FROM campuscore_auth.\"User\" WHERE id = ?", String.class, userId));
    }

    @ParameterizedTest
    @EnumSource(SessionMutation.class)
    void passwordResetSerializesWithWaitingSessionMutations(SessionMutation mutation) throws Exception {
        String userId = insertUser("ACTIVE");
        String refreshToken = login.login(userId + "@auth-race.invalid", "correct-password",
                "127.0.0.1", "auth-reset-race").response().refreshToken();
        IssuedChallengeToken issued = AuthChallengeTokenService.issue();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO campuscore_auth.\"AuthChallenge\""
                        + " (id, \"userId\", purpose, \"tokenHash\", \"expiresAt\", \"attemptCount\", \"lastSentAt\", \"createdAt\")"
                        + " VALUES (?, ?, 'PASSWORD_RESET', ?, ?, 0, ?, ?)",
                issued.challengeId(), userId, issued.tokenHash(),
                Timestamp.from(now.plusSeconds(1_800)), Timestamp.from(now), Timestamp.from(now));
        PasswordResetConfirmRequest resetRequest =
                new PasswordResetConfirmRequest(issued.rawToken(), "reset-wins-password");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicInteger mutationPid = new AtomicInteger();
        AtomicReference<Future<String>> pending = new AtomicReference<>();
        CountDownLatch mutationStarted = new CountDownLatch(1);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.execute("SET LOCAL statement_timeout = '10s'");
                int resetPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                // This is the first lock taken by consumeValidChallenge. Hold
                // it before scheduling the competing real service call.
                jdbc.queryForObject("SELECT id FROM campuscore_auth.\"User\" WHERE id = ? FOR UPDATE",
                        String.class, userId);
                pending.set(pool.submit(() -> {
                    try {
                        return new TransactionTemplate(transactionManager).execute(other -> {
                            jdbc.execute("SET LOCAL statement_timeout = '10s'");
                            mutationPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                            mutationStarted.countDown();
                            switch (mutation) {
                                case REFRESH -> login.refresh(refreshToken, "127.0.0.1", "auth-reset-race");
                                case LOGOUT -> login.logout(userId, refreshToken);
                                case CHANGE_PASSWORD -> login.changePassword(userId, "correct-password", "stale-password-change");
                            }
                            return "SUCCESS";
                        });
                    } catch (BadCredentialsException invalidated) {
                        return "INVALID_REFRESH";
                    } catch (ResponseStatusException invalidated) {
                        if (mutation == SessionMutation.CHANGE_PASSWORD && invalidated.getStatusCode().value() == 400) {
                            return "INVALID_OLD_PASSWORD";
                        }
                        throw invalidated;
                    }
                }));
                await(mutationStarted);
                // Observe an actual PostgreSQL wait edge, not a timed guess.
                // Before the repair, refresh/logout already own Session here
                // and reset's DELETE closes a deadlock cycle.
                Awaitility.await().atMost(Duration.ofSeconds(8)).until(() -> Boolean.TRUE.equals(
                        jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class,
                                resetPid, mutationPid.get())));
                lifecycle.confirmPasswordReset(resetRequest);
            });
            String expected = switch (mutation) {
                case REFRESH -> "INVALID_REFRESH";
                case LOGOUT -> "SUCCESS";
                case CHANGE_PASSWORD -> "INVALID_OLD_PASSWORD";
            };
            assertEquals(expected, pending.get().get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));
        }
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM campuscore_auth.\"Session\" WHERE \"userId\" = ?", Integer.class, userId));
        assertTrue(passwordEncoder.matches("reset-wins-password", jdbc.queryForObject(
                "SELECT password FROM campuscore_auth.\"User\" WHERE id = ?", String.class, userId)));
        assertNotNull(jdbc.queryForObject(
                "SELECT \"consumedAt\" FROM campuscore_auth.\"AuthChallenge\" WHERE id = ?", Timestamp.class,
                issued.challengeId()));
        assertEquals("AUTH_CHALLENGE_INVALID", assertThrows(DomainException.class,
                () -> lifecycle.confirmPasswordReset(resetRequest)).code());
    }

    private enum SessionMutation {
        REFRESH, LOGOUT, CHANGE_PASSWORD
    }

    @Test
    void passwordResetRevokesTheRotatedSessionWhenRefreshCommitsFirst() throws Exception {
        String userId = insertUser("ACTIVE");
        String refreshToken = login.login(userId + "@auth-race.invalid", "correct-password",
                "127.0.0.1", "auth-refresh-first").response().refreshToken();
        IssuedChallengeToken issued = insertChallenge(userId, Purpose.PASSWORD_RESET);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicInteger resetPid = new AtomicInteger();
        AtomicReference<Future<?>> pendingReset = new AtomicReference<>();
        AtomicReference<String> rotatedToken = new AtomicReference<>();
        CountDownLatch resetStarted = new CountDownLatch(1);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.execute("SET LOCAL statement_timeout = '10s'");
                int refreshPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                rotatedToken.set(login.refresh(refreshToken, "127.0.0.1", "auth-refresh-first")
                        .response().refreshToken());
                pendingReset.set(pool.submit(() -> new TransactionTemplate(transactionManager).execute(other -> {
                    jdbc.execute("SET LOCAL statement_timeout = '10s'");
                    resetPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    resetStarted.countDown();
                    return lifecycle.confirmPasswordReset(
                            new PasswordResetConfirmRequest(issued.rawToken(), "reset-after-refresh"));
                })));
                await(resetStarted);
                Awaitility.await().atMost(Duration.ofSeconds(8)).until(() -> Boolean.TRUE.equals(
                        jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class,
                                refreshPid, resetPid.get())));
            });
            pendingReset.get().get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));
        }
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM campuscore_auth.\"Session\" WHERE \"userId\" = ?", Integer.class, userId));
        assertThrows(BadCredentialsException.class,
                () -> login.refresh(rotatedToken.get(), "127.0.0.1", "auth-refresh-first"));
    }

    @ParameterizedTest
    @EnumSource(Purpose.class)
    void concurrentChallengeConsumptionSucceedsExactlyOnce(Purpose purpose) throws Exception {
        String userId = insertUser(purpose == Purpose.EMAIL_VERIFICATION ? "PENDING_VERIFICATION" : "ACTIVE");
        IssuedChallengeToken issued = insertChallenge(userId, purpose);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> calls = List.of(
                    pool.submit(() -> consumeChallenge(purpose, issued, ready, start)),
                    pool.submit(() -> consumeChallenge(purpose, issued, ready, start)));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> call : calls) {
                outcomes.add(call.get(15, TimeUnit.SECONDS));
            }
            assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count());
            assertEquals(1, outcomes.stream().filter("AUTH_CHALLENGE_INVALID"::equals).count());
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));
        }
        assertNotNull(jdbc.queryForObject(
                "SELECT \"consumedAt\" FROM campuscore_auth.\"AuthChallenge\" WHERE id = ?", Timestamp.class,
                issued.challengeId()));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT status FROM campuscore_auth.\"User\" WHERE id = ?", String.class, userId));
    }

    private String consumeChallenge(Purpose purpose, IssuedChallengeToken issued,
            CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            if (purpose == Purpose.EMAIL_VERIFICATION) {
                lifecycle.confirmEmail(new ChallengeTokenRequest(issued.rawToken()));
            } else {
                lifecycle.confirmPasswordReset(new PasswordResetConfirmRequest(issued.rawToken(), "single-use-password"));
            }
            return "SUCCESS";
        } catch (DomainException rejected) {
            return rejected.code();
        }
    }

    private IssuedChallengeToken insertChallenge(String userId, Purpose purpose) {
        IssuedChallengeToken issued = AuthChallengeTokenService.issue();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO campuscore_auth.\"AuthChallenge\""
                        + " (id, \"userId\", purpose, \"tokenHash\", \"expiresAt\", \"attemptCount\", \"lastSentAt\", \"createdAt\")"
                        + " VALUES (?, ?, ?, ?, ?, 0, ?, ?)",
                issued.challengeId(), userId, purpose.name(), issued.tokenHash(),
                Timestamp.from(now.plusSeconds(1_800)), Timestamp.from(now), Timestamp.from(now));
        return issued;
    }

    private void resend(String email, String ip, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        lifecycle.resendVerification(new EmailRequest(email), ip);
    }

    private void wrongLogin(String email, String ip, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            login.login(email, "definitely-wrong", ip, "auth-race-test");
        } catch (BadCredentialsException expected) {
            // Every call is intentionally invalid; the database counter is
            // the assertion made after all transactions have committed.
        }
    }

    private RegistrationAttempt register(String email, String ip, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            login.register(new RegisterRequest(email, "correct-password", "Race", "User", null, null, null, null),
                    ip,
                    "auth-race-test");
            return new RegistrationAttempt(true, null);
        } catch (DomainException exception) {
            return new RegistrationAttempt(false, exception.code());
        }
    }

    private String insertUser(String status) {
        String id = "auth-race-" + UUID.randomUUID();
        createdUserIds.add(id);
        jdbc.update("INSERT INTO campuscore_auth.\"User\""
                        + " (id, email, password, \"firstName\", \"lastName\", status, \"emailVerified\","
                        + " \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, 'Race', 'User', ?, ?, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id,
                id + "@auth-race.invalid",
                passwordEncoder.encode("correct-password"),
                status,
                "ACTIVE".equals(status));
        return id;
    }

    private void rememberRateLimit(String scope, String rawKey) {
        createdRateLimitKeys.add(new String[]{scope, AuthChallengeTokenService.sha256(rawKey)});
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Auth race worker interrupted", interrupted);
        }
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record RegistrationAttempt(boolean accepted, String code) {
    }
}
