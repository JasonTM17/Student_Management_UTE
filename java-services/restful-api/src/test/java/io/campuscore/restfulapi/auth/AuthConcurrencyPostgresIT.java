package io.campuscore.restfulapi.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.campuscore.restfulapi.auth.service.AuthChallengeTokenService;
import io.campuscore.restfulapi.auth.service.AuthLifecycleService;
import io.campuscore.restfulapi.auth.service.AuthLoginService;
import io.campuscore.restfulapi.auth.web.AuthDtos.EmailRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.RegisterRequest;
import io.campuscore.restfulapi.web.DomainException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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
