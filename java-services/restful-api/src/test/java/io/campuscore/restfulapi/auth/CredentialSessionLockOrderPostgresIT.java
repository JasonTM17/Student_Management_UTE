package io.campuscore.restfulapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.auth.service.AuthLoginService;
import io.campuscore.restfulapi.auth.service.PasswordResetService;
import io.campuscore.restfulapi.exception.AppException;
import io.campuscore.restfulapi.mail.service.EmailService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Root-owned real PostgreSQL schedules with explicit User-before-child-lock assertions. */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = {"spring.flyway.enabled=false", "deepseek.enabled=false"})
@ActiveProfiles({"test", "persistence"})
class CredentialSessionLockOrderPostgresIT {
    private static final int WAIT_SECONDS = 20;
    @Autowired private AuthLoginService auth;
    @Autowired private PasswordResetService resets;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private AuthUserRepository users;
    @MockitoSpyBean private NamedParameterJdbcTemplate namedJdbc;
    @MockitoBean private EmailService email;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_COUNCIL_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CAMPUSCORE_COUNCIL_POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("CAMPUSCORE_COUNCIL_POSTGRES_PASSWORD"));
    }

    @AfterEach
    void resetBarriers() { reset(users, namedJdbc); }

    @Test
    void resetRequestWaitsOnUserBeforeInvalidatingExistingLink() throws Exception {
        Fixture f = fixture();
        jdbc.update("INSERT INTO campuscore_auth.\"PasswordResetToken\""
                + " (id,\"userId\",\"tokenHash\",\"expiresAt\") VALUES (?, ?, ?, CURRENT_TIMESTAMP+INTERVAL '5 minutes')",
                "credential-link-" + f.id(), f.id(), digest(f.id()));
        String thread = "credential-request-" + f.id();
        var reachedFirstLock = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().equals(thread)) reachedFirstLock.countDown();
            return invocation.callRealMethod();
        }).when(users).lockUserRow(f.id());
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            String sql = invocation.getArgument(0, String.class);
            if (Thread.currentThread().getName().equals(thread) && sql.contains("PasswordResetToken")
                    && sql.contains("SET \"usedAt\"")) reachedFirstLock.countDown();
            return result;
        }).when(namedJdbc).update(anyString(), any(SqlParameterSource.class));
        var pool = Executors.newSingleThreadExecutor();
        Future<?> request = null;
        try (Connection holder = connection()) {
            holder.setAutoCommit(false);
            lockRow(holder, "User", "id", f.id());
            try {
                request = pool.submit(() -> { Thread.currentThread().setName(thread); resets.requestReset(f.email(), "127.0.0.1"); return null; });
                await(reachedFirstLock);
                assertThat(request.isDone()).isFalse();
                // Token-first issuance holds this row while its FK insert waits
                // on User. A concurrent User-first consumption completes the cycle.
                assertChildRowFree("PasswordResetToken", f.id());
            } finally { holder.rollback(); }
            request.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void refreshWaitsOnUserBeforeSessionAndCannotResurrectRevokedCredentials() throws Exception {
        queuedAuthentication(false);
    }

    @Test
    void loginChallengeWaitsOnUserBeforeChallengeAndCannotResurrectRevokedCredentials() throws Exception {
        queuedAuthentication(true);
    }

    private void queuedAuthentication(boolean challenge) throws Exception {
        Fixture f = fixture();
        String refresh = auth.login(f.email(), f.before(), "127.0.0.1", "credential-lock-it").response().refreshToken();
        String challengeId = "credential-queued-" + UUID.randomUUID();
        String code = "314159";
        if (challenge) jdbc.update("INSERT INTO campuscore_auth.\"TwoFactorChallenge\""
                + " (id,\"userId\",purpose,\"codeHash\",\"expiresAt\",attempts,\"createdAt\")"
                + " VALUES (?,?,'LOGIN',?,CURRENT_TIMESTAMP+INTERVAL '5 minutes',0,CURRENT_TIMESTAMP)",
                challengeId, f.id(), digest(f.id() + ':' + code));
        var writerHoldsUser = new CountDownLatch(1);
        var releaseWriter = new CountDownLatch(1);
        var waiterAtUser = new CountDownLatch(1);
        String writerName = "credential-writer-" + f.id(), waiterName = "credential-waiter-" + f.id();
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            Object result = invocation.callRealMethod();
            if (Thread.currentThread().getName().equals(writerName)
                    && sql.contains("SET \"password\"") && sql.contains("\"credentialVersion\" + 1")) {
                writerHoldsUser.countDown(); await(releaseWriter);
            }
            return result;
        }).when(namedJdbc).update(anyString(), any(SqlParameterSource.class));
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().equals(waiterName)) waiterAtUser.countDown();
            return invocation.callRealMethod();
        }).when(users).lockUserRow(f.id());
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> writer = pool.submit(() -> { Thread.currentThread().setName(writerName); auth.changePassword(f.id(), f.before(), f.after()); return null; });
            await(writerHoldsUser);
            Future<?> waiter = pool.submit(() -> {
                Thread.currentThread().setName(waiterName);
                return challenge ? auth.completeLogin(challengeId, code, "127.0.0.1", "credential-lock-it")
                        : auth.refresh(refresh, "127.0.0.1", "credential-lock-it");
            });
            await(waiterAtUser);
            assertThat(waiter.isDone()).isFalse();
            assertChildRowFree(challenge ? "TwoFactorChallenge" : "Session", f.id());
            releaseWriter.countDown();
            writer.get(WAIT_SECONDS, TimeUnit.SECONDS);
            try { waiter.get(WAIT_SECONDS, TimeUnit.SECONDS); throw new AssertionError("Stale authentication must not mint a session"); }
            catch (ExecutionException expected) { assertThat(expected.getCause()).isInstanceOf(challenge ? AppException.class : BadCredentialsException.class); }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM campuscore_auth.\"Session\" WHERE \"userId\"=?", Integer.class, f.id())).isZero();
            if (challenge) assertThat(jdbc.queryForObject("SELECT \"consumedAt\" IS NOT NULL FROM campuscore_auth.\"TwoFactorChallenge\" WHERE id=?", Boolean.class, challengeId)).isTrue();
            assertThat(auth.login(f.email(), f.after(), "127.0.0.1", "credential-lock-it").response().user().id()).isEqualTo(f.id());
            assertThat(users.findById(f.id()).orElseThrow().credentialVersion()).isEqualTo(1);
        } finally {
            releaseWriter.countDown(); pool.shutdownNow();
            assertThat(pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertChildRowFree(String table, String userId) throws Exception {
        try (Connection probe = connection()) {
            probe.setAutoCommit(false);
            try { lockRow(probe, table, "userId", userId); } finally { probe.rollback(); }
        }
    }

    private static void lockRow(Connection connection, String table, String key, String id) throws Exception {
        try (var statement = connection.prepareStatement("SELECT id FROM campuscore_auth.\"" + table + "\" WHERE \"" + key + "\"=? FOR UPDATE NOWAIT")) {
            statement.setString(1, id);
            try (var rows = statement.executeQuery()) { assertThat(rows.next()).isTrue(); }
        }
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        Fixture f = new Fixture("credential-queue-" + suffix, "credential-queue-" + suffix + "@example.invalid", "BeforeRotation9-" + suffix, "AfterRotation9-" + suffix);
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (id,email,password,\"firstName\",\"lastName\",status,\"emailVerified\")"
                + " VALUES (?,?,?,'Synthetic','Credential queue','ACTIVE',TRUE)", f.id(), f.email(), encoder.encode(f.before()));
        return f;
    }

    private static String digest(String value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    private static void await(CountDownLatch barrier) throws InterruptedException { assertThat(barrier.await(WAIT_SECONDS, TimeUnit.SECONDS)).as("deterministic barrier").isTrue(); }
    private static Connection connection() throws Exception { return DriverManager.getConnection(System.getenv("CAMPUSCORE_COUNCIL_POSTGRES_URL"), System.getenv("CAMPUSCORE_COUNCIL_POSTGRES_USER"), System.getenv("CAMPUSCORE_COUNCIL_POSTGRES_PASSWORD")); }
    static boolean postgresTargetConfigured() {
        String url = System.getenv("CAMPUSCORE_COUNCIL_POSTGRES_URL");
        if (url == null || !url.startsWith("jdbc:postgresql:")) return false;
        try { URI uri = URI.create(url.substring(5)); return "127.0.0.1".equals(uri.getHost()) && uri.getPort() == 5452 && "/campuscore_council_race_credential_20261016".equals(uri.getPath()); }
        catch (IllegalArgumentException invalid) { return false; }
    }
    private record Fixture(String id, String email, String before, String after) { }
}
