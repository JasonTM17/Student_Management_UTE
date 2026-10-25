package io.campuscore.restfulapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.auth.service.AdminUserMutationService;
import io.campuscore.restfulapi.exception.DomainException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** PostgreSQL proof that concurrent role changes cannot remove every active super administrator. */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = {"spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false"})
@ActiveProfiles({"test", "persistence"})
class LastActiveSuperAdminProtectionPostgresIT {

    @Autowired private NamedParameterJdbcTemplate jdbc;
    @Autowired private AdminUserMutationService users;
    // The spy calls the real repository queries and only controls this test's interleaving.
    @MockitoSpyBean private AuthUserRepository authUsers;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_E2E_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "postgres"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_PASSWORD", "postgres"));
    }

    static boolean postgresTargetConfigured() {
        String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_URL");
        String user = valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "postgres");
        if (url == null || !url.startsWith("jdbc:postgresql:") || !"postgres".equals(user)) {
            return false;
        }
        URI target = URI.create(url.replaceFirst("^jdbc:", ""));
        return "postgresql".equals(target.getScheme())
                && target.getHost() != null
                && List.of("localhost", "127.0.0.1", "::1").contains(target.getHost())
                && target.getUserInfo() == null
                && target.getPath() != null
                && target.getPath().matches("/(?:cx6|campuscore)_[a-z0-9_]+");
    }

    @Test
    void concurrentDemotionsCannotRemoveEveryActiveSuperAdmin() throws Exception {
        URI target = URI.create(System.getenv("CAMPUSCORE_E2E_POSTGRES_URL").replaceFirst("^jdbc:", ""));
        assertThat(target.getScheme()).isEqualTo("postgresql");
        assertThat(target.getHost()).isIn("localhost", "127.0.0.1", "::1");
        assertThat(target.getUserInfo()).isNull();
        assertThat(target.getPath()).matches("/(?:cx6|campuscore)_[a-z0-9_]+");
        assertThat(valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "postgres")).isEqualTo("postgres");

        String suffix = UUID.randomUUID().toString().replace("-", "");
        String first = "cx6-sa-first-" + suffix;
        String second = "cx6-sa-second-" + suffix;
        String roleId = jdbc.queryForObject(
                "SELECT \"id\" FROM \"campuscore_auth\".\"Role\" WHERE \"name\" = 'SUPER_ADMIN'",
                new MapSqlParameterSource(), String.class);
        jdbc.update("DELETE FROM \"campuscore_auth\".\"UserRole\" WHERE \"roleId\" = :roleId",
                new MapSqlParameterSource("roleId", roleId));
        insertUser(first, "first", suffix);
        insertUser(second, "second", suffix);
        assignRole(first, roleId, "first", suffix);
        assignRole(second, roleId, "second", suffix);

        assertThat(activeSuperAdminCount()).isEqualTo(2);
        AtomicInteger lockCalls = new AtomicInteger();
        AtomicInteger countCalls = new AtomicInteger();
        CountDownLatch firstCounted = new CountDownLatch(1);
        CountDownLatch releaseFirstCount = new CountDownLatch(1);
        CountDownLatch secondLockAttempt = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (lockCalls.incrementAndGet() == 2) {
                secondLockAttempt.countDown();
            }
            return invocation.callRealMethod();
        }).when(authUsers).lockSuperAdminRole();
        doAnswer(invocation -> {
            long count = (long) invocation.callRealMethod();
            if (countCalls.incrementAndGet() == 1) {
                firstCounted.countDown();
                if (!releaseFirstCount.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("First role-count transaction was not released");
                }
            }
            return count;
        }).when(authUsers).countActiveSuperAdmins();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstChange = executor.submit(() -> demote(first));
            try {
                assertThat(firstCounted.await(10, TimeUnit.SECONDS)).isTrue();
                var secondChange = executor.submit(() -> demote(second));
                assertThat(secondLockAttempt.await(10, TimeUnit.SECONDS)).isTrue();
                releaseFirstCount.countDown();
                List<String> outcomes = List.of(
                        firstChange.get(20, TimeUnit.SECONDS), secondChange.get(20, TimeUnit.SECONDS));
                assertThat(outcomes).containsExactlyInAnyOrder("UPDATED", "LAST_SUPER_ADMIN_REQUIRED");
                assertThat(activeSuperAdminCount()).isEqualTo(1);
            } finally {
                releaseFirstCount.countDown();
            }
        }
    }

    private String demote(String userId) {
        try {
            users.update(userId, Map.of("role", "ADMIN"), true, "cx6-test-actor");
            return "UPDATED";
        } catch (DomainException exception) {
            return exception.getCode();
        }
    }

    private void insertUser(String userId, String label, String suffix) {
        jdbc.update("INSERT INTO \"campuscore_auth\".\"User\""
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\","
                        + " \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (:id, :email, 'fixture-hash', 'CX6', :label, 'ACTIVE', TRUE, TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                new MapSqlParameterSource().addValue("id", userId)
                        .addValue("email", label + "." + suffix + "@cx6.invalid")
                        .addValue("label", label));
    }

    private void assignRole(String userId, String roleId, String label, String suffix) {
        jdbc.update("INSERT INTO \"campuscore_auth\".\"UserRole\" (\"id\", \"userId\", \"roleId\")"
                        + " VALUES (:id, :userId, :roleId)",
                new MapSqlParameterSource().addValue("id", "cx6-role-" + label + "-" + suffix)
                        .addValue("userId", userId)
                        .addValue("roleId", roleId));
    }

    private int activeSuperAdminCount() {
        return jdbc.queryForObject("SELECT COUNT(DISTINCT u.\"id\")"
                        + " FROM \"campuscore_auth\".\"User\" u"
                        + " JOIN \"campuscore_auth\".\"UserRole\" ur ON ur.\"userId\" = u.\"id\""
                        + " JOIN \"campuscore_auth\".\"Role\" r ON r.\"id\" = ur.\"roleId\""
                        + " WHERE u.\"status\" = 'ACTIVE' AND r.\"name\" = 'SUPER_ADMIN'",
                new MapSqlParameterSource(), Integer.class);
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
