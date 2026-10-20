package io.campuscore.restfulapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import static org.mockito.ArgumentMatchers.eq;

import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.auth.service.AuthLoginService;
import io.campuscore.restfulapi.auth.service.PasswordResetService;
import io.campuscore.restfulapi.auth.service.TwoFactorService;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.Cookie;

/** Root-owned real PostgreSQL schedules with explicit User-before-child-lock assertions. */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = {"spring.flyway.enabled=false", "deepseek.enabled=false"})
@ActiveProfiles({"test", "persistence"})
class CredentialSessionLockOrderPostgresIT {
    private static final int WAIT_SECONDS = 20;
    @Autowired private AuthLoginService auth;
    @Autowired private PasswordResetService resets;
    @Autowired private TwoFactorService twoFactor;
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

    /**
     * Deterministic losing schedule for the 2FA disable flow: the confirm
     * transaction pins its session id, then a refresh commits the rotation
     * before the confirm reaches the User row lock. Because the pin names the
     * stable Session.id — which survives rotation — the caller's rotated
     * session is still the one row kept. A hash-keyed keep-exclusion would
     * instead delete the rotated row and break the preserved-session
     * contract.
     */
    @Test
    void confirmDisableKeepsThePinnedSessionAcrossACommittedRotation() throws Exception {
        Fixture f = fixture();
        String refresh0 = auth.login(f.email(), f.before(), "127.0.0.1", "race-it").response().refreshToken();
        auth.login(f.email(), f.before(), "127.0.0.1", "race-it-2").response().refreshToken();
        // Enabled after the logins: with the flag already on, login returns a
        // challenge instead of issuing refresh sessions.
        jdbc.update("UPDATE campuscore_auth.\"User\" SET \"twoFactorEnabled\" = TRUE WHERE \"id\" = ?", f.id());
        String pinnedSession = jdbc.queryForObject(
                "SELECT \"id\" FROM campuscore_auth.\"Session\" WHERE \"userId\"=? AND \"refreshToken\"=?",
                String.class, f.id(), digest(refresh0));
        String challengeId = "disable-race-" + UUID.randomUUID();
        String code = "271828";
        jdbc.update("INSERT INTO campuscore_auth.\"TwoFactorChallenge\""
                + " (id,\"userId\",purpose,\"codeHash\",\"expiresAt\",attempts,\"createdAt\")"
                + " VALUES (?,?,'DISABLE',?,CURRENT_TIMESTAMP+INTERVAL '5 minutes',0,CURRENT_TIMESTAMP)",
                challengeId, f.id(), digest(f.id() + ':' + code));

        var pinTaken = new CountDownLatch(1);
        var rotationCommitted = new CountDownLatch(1);
        String confirmThread = "disable-confirm-" + f.id();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (Thread.currentThread().getName().equals(confirmThread)) {
                pinTaken.countDown();
                await(rotationCommitted);
            }
            return result;
        }).when(users).findSessionIdByRefreshHash(eq(f.id()), anyString());

        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> confirm = pool.submit(() -> {
                Thread.currentThread().setName(confirmThread);
                MockHttpServletRequest request = new MockHttpServletRequest();
                request.setCookies(new Cookie("cc_refresh_token", refresh0));
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
                try {
                    twoFactor.confirmDisable(f.id(), challengeId, code);
                } finally {
                    RequestContextHolder.resetRequestAttributes();
                }
                return null;
            });
            await(pinTaken);
            // The review's losing interleave: the rotation commits while the
            // confirm sits between its pin and the credential lock.
            String refresh1 = auth.refresh(refresh0, "127.0.0.1", "race-it").response().refreshToken();
            rotationCommitted.countDown();
            confirm.get(WAIT_SECONDS, TimeUnit.SECONDS);

            Integer sessions = jdbc.queryForObject(
                    "SELECT count(*) FROM campuscore_auth.\"Session\" WHERE \"userId\"=?",
                    Integer.class, f.id());
            assertThat(sessions).isEqualTo(1);
            String keptId = jdbc.queryForObject(
                    "SELECT \"id\" FROM campuscore_auth.\"Session\" WHERE \"userId\"=?",
                    String.class, f.id());
            assertThat(keptId).isEqualTo(pinnedSession);
            String keptHash = jdbc.queryForObject(
                    "SELECT \"refreshToken\" FROM campuscore_auth.\"Session\" WHERE \"id\"=?",
                    String.class, pinnedSession);
            assertThat(keptHash).isEqualTo(digest(refresh1));
            // The browser holding the rotated token still refreshes normally.
            assertThat(auth.refresh(refresh1, "127.0.0.1", "race-it").response().user().id())
                    .isEqualTo(f.id());
            assertThat(users.findById(f.id()).orElseThrow().credentialVersion()).isEqualTo(1);
        } finally {
            rotationCommitted.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * Same raced schedule against single-session logout: the pin is taken
     * before the User lock, a refresh commits the rotation underneath it, and
     * the id-keyed delete still removes the rotated row. A hash-keyed delete
     * would miss and leave the session alive past logout.
     */
    @Test
    void logoutDropsThePinnedSessionAcrossACommittedRotation() throws Exception {
        Fixture f = fixture();
        String refresh0 = auth.login(f.email(), f.before(), "127.0.0.1", "race-it").response().refreshToken();

        var pinTaken = new CountDownLatch(1);
        var rotationCommitted = new CountDownLatch(1);
        String logoutThread = "logout-race-" + f.id();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (Thread.currentThread().getName().equals(logoutThread)) {
                pinTaken.countDown();
                await(rotationCommitted);
            }
            return result;
        }).when(users).findSessionIdByRefreshHash(eq(f.id()), anyString());

        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> logout = pool.submit(() -> {
                Thread.currentThread().setName(logoutThread);
                auth.logout(f.id(), refresh0);
                return null;
            });
            await(pinTaken);
            String refresh1 = auth.refresh(refresh0, "127.0.0.1", "race-it").response().refreshToken();
            rotationCommitted.countDown();
            logout.get(WAIT_SECONDS, TimeUnit.SECONDS);

            Integer sessions = jdbc.queryForObject(
                    "SELECT count(*) FROM campuscore_auth.\"Session\" WHERE \"userId\"=?",
                    Integer.class, f.id());
            assertThat(sessions).isZero();
            assertThatThrownBy(() -> auth.refresh(refresh1, "127.0.0.1", "race-it"))
                    .isInstanceOf(BadCredentialsException.class);
        } finally {
            rotationCommitted.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
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
