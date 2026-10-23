package io.campuscore.restfulapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.auth.service.AuthLoginService;
import io.campuscore.restfulapi.exception.AppException;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * PostgreSQL-only regression for credential rotation racing session issuance.
 * Login resolves the account snapshot before taking the User row lock and
 * re-reads it afterwards, so a password rotation that commits while login
 * waits on the lock is always verified against the rotated hash. Fresh
 * tokens carry the bumped credential epoch; artifacts minted before the
 * commit stay dead. H2 has no real {@code FOR UPDATE} queueing, so the
 * ordering assertions need an actual PostgreSQL target.
 */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false" })
@ActiveProfiles({"test", "persistence"})
class AuthCredentialRevocationPostgresIT {

    private static final int WAIT_SECONDS = 20;

    @Autowired private AuthLoginService authLogin;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtDecoder jwtDecoder;
    @MockitoSpyBean private NamedParameterJdbcTemplate namedJdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_COUNCIL_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_COUNCIL_POSTGRES_USER", "campuscore"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_COUNCIL_POSTGRES_PASSWORD", "postgres"));
    }

    @Test
    void loginQueuesBehindRotationThenVerifiesTheRotatedCredential() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String userId = "cred-user-" + suffix;
        String email = "cred-" + suffix + "@campuscore.edu";
        String rawPassword = "CredRace-" + suffix;
        String rotatedPassword = rawPassword + "-rotated";
        jdbc.update("INSERT INTO campuscore_auth.\"User\""
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\","
                        + " \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, 'ACTIVE', TRUE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, email, passwordEncoder.encode(rawPassword), "Cred", "Race");

        AuthLoginService.LoginResult preRotation =
                authLogin.login(email, rawPassword, "127.0.0.1", "cred-it");
        String staleAccess = preRotation.response().accessToken();
        String staleRefresh = preRotation.response().refreshToken();
        assertThat(((Number) jwtDecoder.decode(staleAccess).getClaim("credentialVersion")).longValue())
                .as("pre-rotation access token is minted at revision zero")
                .isZero();

        CountDownLatch rotationHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseRotation = new CountDownLatch(1);
        String rotationThread = "pgw-rotate-" + suffix;
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            Object result = invocation.callRealMethod();
            if (Thread.currentThread().getName().equals(rotationThread)
                    && sql.contains("FOR UPDATE")) {
                // The rotation transaction now holds the User row lock. Pause
                // before the version bump; a login that does not re-read under
                // the lock would verify the stale pre-rotation snapshot.
                rotationHoldsLock.countDown();
                if (!releaseRotation.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("rotation release barrier timeout");
                }
            }
            return result;
        }).when(namedJdbc).queryForList(anyString(), any(SqlParameterSource.class), any(Class.class));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> rotation = pool.submit(() -> {
                Thread.currentThread().setName(rotationThread);
                authLogin.changePassword(userId, rawPassword, rotatedPassword, staleRefresh);
                return null;
            });
            assertThat(rotationHoldsLock.await(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as("rotation must hold the user row lock before login starts")
                    .isTrue();

            Future<AuthLoginService.LoginResult> login = pool.submit(() -> {
                Thread.currentThread().setName("pgw-login-" + suffix);
                return authLogin.login(email, rotatedPassword, "127.0.0.1", "cred-it");
            });
            Thread.sleep(800);
            assertThat(login.isDone())
                    .as("login must queue behind the rotation user lock")
                    .isFalse();

            // While rotation holds the User lock a NOWAIT probe must fail,
            // proving the queue is a real PostgreSQL row lock.
            try (Connection probe = DriverManager.getConnection(
                    System.getenv("CAMPUSCORE_COUNCIL_POSTGRES_URL"),
                    valueOr("CAMPUSCORE_COUNCIL_POSTGRES_USER", "campuscore"),
                    valueOr("CAMPUSCORE_COUNCIL_POSTGRES_PASSWORD", "postgres"))) {
                probe.setAutoCommit(false);
                assertThatThrownBy(() -> {
                    try (PreparedStatement statement = probe.prepareStatement(
                            "SELECT \"id\" FROM campuscore_auth.\"User\" WHERE \"id\" = ? FOR UPDATE NOWAIT")) {
                        statement.setString(1, userId);
                        try (var ignored = statement.executeQuery()) {
                            // reachable only if the rotation released the row early
                        }
                    }
                }).as("user row must stay locked while rotation is paused")
                        .isInstanceOf(SQLException.class);
                probe.rollback();
            }

            releaseRotation.countDown();
            rotation.get(WAIT_SECONDS, TimeUnit.SECONDS);
            AuthLoginService.LoginResult postRotation = login.get(WAIT_SECONDS, TimeUnit.SECONDS);

            long epoch = jdbc.queryForObject(
                    "SELECT \"credentialVersion\" FROM campuscore_auth.\"User\" WHERE \"id\" = ?",
                    Long.class, userId);
            assertThat(epoch).as("rotation commits exactly one epoch bump").isEqualTo(1L);
            String freshAccess = postRotation.response().accessToken();
            assertThat(((Number) jwtDecoder.decode(freshAccess).getClaim("credentialVersion")).longValue())
                    .as("a token issued after waiting on the lock carries the rotated epoch; "
                            + "a stale-snapshot issue would mint revision zero or fail the rotated password")
                    .isEqualTo(1L);

            assertThatThrownBy(() -> authLogin.refresh(staleRefresh, "127.0.0.1", "cred-it"))
                    .as("the pre-rotation refresh token stays revoked")
                    .isInstanceOf(BadCredentialsException.class);
        } finally {
            releaseRotation.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void rotationConsumesPendingLoginChallengeAtomically() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String userId = "cred-challenge-" + suffix;
        String email = "cred-challenge-" + suffix + "@campuscore.edu";
        String rawPassword = "CredChallenge-" + suffix;
        String challengeId = "cred-login-challenge-" + suffix;
        jdbc.update("INSERT INTO campuscore_auth.\"User\""
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\","
                        + " \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, 'ACTIVE', TRUE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, email, passwordEncoder.encode(rawPassword), "Cred", "Challenge");
        jdbc.update("INSERT INTO campuscore_auth.\"TwoFactorChallenge\""
                        + " (\"id\", \"userId\", \"purpose\", \"codeHash\", \"expiresAt\", \"attempts\")"
                        + " VALUES (?, ?, 'LOGIN', ?, CURRENT_TIMESTAMP + INTERVAL '1 hour', 0)",
                challengeId, userId, "0".repeat(64));

        AuthLoginService.LoginResult preRotation =
                authLogin.login(email, rawPassword, "127.0.0.1", "cred-it");
        authLogin.changePassword(userId, rawPassword, rawPassword + "-rotated",
                preRotation.response().refreshToken());

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM campuscore_auth.\"TwoFactorChallenge\""
                                + " WHERE \"id\" = ? AND \"consumedAt\" IS NOT NULL",
                        Integer.class, challengeId))
                .as("the pending LOGIN challenge is consumed inside the rotation transaction")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM campuscore_auth.\"Session\" WHERE \"userId\" = ?",
                        Integer.class, userId))
                .as("the calling device's session survives, re-issued under the same row")
                .isEqualTo(1);
        assertThatThrownBy(() -> authLogin.completeLogin(challengeId, "000000", "127.0.0.1", "cred-it"))
                .as("a challenge consumed by credential replacement can no longer mint a session")
                .isInstanceOf(AppException.class);
        assertThat(jdbc.queryForObject(
                        "SELECT \"credentialVersion\" FROM campuscore_auth.\"User\" WHERE \"id\" = ?",
                        Long.class, userId))
                .isEqualTo(1L);
    }

    static boolean postgresTargetConfigured() {
        String url = System.getenv("CAMPUSCORE_COUNCIL_POSTGRES_URL");
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            return false;
        }
        try {
            URI uri = URI.create(url.substring("jdbc:".length()));
            String host = uri.getHost();
            boolean loopback = "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host) || "::1".equals(host) || "[::1]".equals(host);
            String path = uri.getPath();
            String database = path != null && path.startsWith("/") ? path.substring(1) : null;
            return loopback && database != null && database.startsWith("campuscore_council_race_");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String valueOr(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
