package io.campuscore.restfulapi.notification;

import static org.assertj.core.api.Assertions.assertThat;

import io.campuscore.restfulapi.audit.AdminAuditRecorder;
import io.campuscore.restfulapi.notification.repository.NotificationWriteRepository;
import io.campuscore.restfulapi.notification.service.NotificationWriteService;
import io.campuscore.restfulapi.notification.web.NotificationWriteDtos.UpdateNotificationRequest;
import java.net.URI;
import java.sql.Connection;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Actual service/JDBC/transactions on one fresh caller-created PostgreSQL database. No mocks/reset. */
@EnabledIfEnvironmentVariable(named = "CAMPUSCORE_NOTIFICATION_AUDIT_ENABLED", matches = "true")
class NotificationAuditConcurrencyPostgresIT {
    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static NotificationWriteService service;
    private static TransactionTemplate transaction;

    @BeforeAll
    static void prepareFreshDisposableDatabase() {
        String url = required("CAMPUSCORE_NOTIFICATION_AUDIT_URL");
        URI target = URI.create(url.replaceFirst("^jdbc:", ""));
        assertThat(target.getScheme()).isEqualTo("postgresql");
        assertThat(target.getHost()).isIn("localhost", "127.0.0.1", "::1");
        assertThat(target.getUserInfo()).isNull();
        assertThat(target.getPath()).matches("/policy_migration_test_[a-z0-9_]+");
        dataSource = new DriverManagerDataSource(url,required("CAMPUSCORE_NOTIFICATION_AUDIT_USER"),required("CAMPUSCORE_NOTIFICATION_AUDIT_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo(target.getPath().substring(1));
        assertThat(jdbc.queryForObject("SELECT to_regclass('notifications.notification')::text",String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT to_regclass('campuscore_audit.\"AdminAudit\"')::text",String.class)).isNull();
        jdbc.execute("CREATE SCHEMA notifications");
        jdbc.execute("""
                CREATE TABLE notifications.notification (
                  id text PRIMARY KEY,user_id text NOT NULL,title text NOT NULL,message text NOT NULL,
                  type text NOT NULL,link text,is_read boolean NOT NULL DEFAULT FALSE,read_at timestamptz,
                  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP)
                """);
        jdbc.execute("CREATE SCHEMA campuscore_audit");
        jdbc.execute("""
                CREATE TABLE campuscore_audit."AdminAudit" (
                  "id" text PRIMARY KEY,"actorId" text,"actorLabel" text,"action" text NOT NULL,
                  "entityType" text NOT NULL,"entityId" text,"summary" text,"beforeState" text,"afterState" text,
                  "createdAt" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP)
                """);
        service = service(new NamedParameterJdbcTemplate(dataSource));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void typeOnlyUpdateAuditsTheOwnerCommittedImmediatelyBeforeItsMutation() throws Exception {
        String id = "concurrent-owner-notification";
        insert(id);
        try (Connection owner = dataSource.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            owner.setAutoCommit(false);
            JdbcTemplate writer = new JdbcTemplate(new SingleConnectionDataSource(owner,true));
            writer.update("UPDATE notifications.notification SET user_id='lecturer' WHERE id=?",id);
            int pid = writer.queryForObject("SELECT pg_backend_pid()",Integer.class);
            var update = executor.submit(() -> transaction.execute(status -> service.update(id,
                    new UpdateNotificationRequest(Set.of("type"),null,null,null,"WARNING",null),"admin-type")));
            try {
                awaitBlocked(pid);
                owner.commit();
                var result = update.get(15,TimeUnit.SECONDS);
                assertThat(result.userId()).isEqualTo("lecturer");
                assertThat(result.type()).isEqualTo("WARNING");
                assertThat(auditCount(id,"NOTIFICATION_UPDATED")).isEqualTo(1);
                assertThat(auditState(id,"beforeState")).contains("userId=lecturer","type=INFO").doesNotContain("student","private body");
                assertThat(auditState(id,"afterState")).contains("userId=lecturer","type=WARNING").doesNotContain("private body");
            } finally {
                owner.rollback();
            }
        }
    }

    @Test
    void competingDeleteHasOneSuccessfulDeletionAndOneAudit() throws Exception {
        String id = "concurrent-delete-notification";
        insert(id);
        try (Connection winner = dataSource.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            winner.setAutoCommit(false);
            SingleConnectionDataSource winnerSource = new SingleConnectionDataSource(winner,true);
            NotificationWriteService first = service(new NamedParameterJdbcTemplate(winnerSource));
            first.delete(id,"admin-winner");
            int pid = new JdbcTemplate(winnerSource).queryForObject("SELECT pg_backend_pid()",Integer.class);
            var second = executor.submit(() -> {
                try {
                    transaction.execute(status -> service.delete(id,"admin-loser"));
                    return "SUCCESS";
                } catch (ResponseStatusException missing) {
                    assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    return "NOT_FOUND";
                }
            });
            try {
                awaitBlocked(pid);
                winner.commit();
                assertThat(second.get(15,TimeUnit.SECONDS)).isEqualTo("NOT_FOUND");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications.notification WHERE id=?",Integer.class,id)).isZero();
                assertThat(auditCount(id,"NOTIFICATION_DELETED")).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT \"actorId\" FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\"=?",String.class,id)).isEqualTo("admin-winner");
            } finally {
                winner.rollback();
            }
        }
    }

    private static NotificationWriteService service(NamedParameterJdbcTemplate template) {
        return new NotificationWriteService(new NotificationWriteRepository(template),new AdminAuditRecorder(template));
    }

    private static void insert(String id) {
        jdbc.update("INSERT INTO notifications.notification(id,user_id,title,message,type) VALUES (?,'student','private title','private body','INFO')",id);
    }

    private static int auditCount(String id,String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\"=? AND \"action\"=?",Integer.class,id,action);
    }

    private static String auditState(String id,String field) {
        // The callers above supply only these two fixed columns.
        if (!Set.of("beforeState","afterState").contains(field)) throw new IllegalArgumentException(field);
        return jdbc.queryForObject("SELECT \"" + field + "\" FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\"=?",String.class,id);
    }

    private static void awaitBlocked(int blocker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime()<deadline) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM pg_stat_activity WHERE datname=current_database() AND ?=ANY(pg_blocking_pids(pid))",Integer.class,blocker)>0) return;
            Thread.sleep(25);
        }
        throw new AssertionError("second mutation must actually wait on the held notification row");
    }

    private static String required(String name) {
        String value=System.getenv(name);
        if (value==null || value.isBlank()) throw new IllegalStateException("Missing disposable test setting: " + name);
        return value;
    }
}
