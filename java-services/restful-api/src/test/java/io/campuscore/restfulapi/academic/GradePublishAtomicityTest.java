package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.campuscore.restfulapi.academic.registration.service.RegistrationService;
import io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService;
import io.campuscore.restfulapi.academic.service.AcademicMutationService;
import io.campuscore.restfulapi.academic.dto.AcademicMutationDtos.GradeUpdate;
import io.campuscore.restfulapi.audit.AdminAuditRecorder;
import io.campuscore.restfulapi.engagement.service.GradePublishNotifier;
import io.campuscore.restfulapi.notification.repository.NotificationWriteRepository;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Real transaction fault points: inbox messages must follow committed grades. */
@SpringJUnitConfig(GradePublishAtomicityTest.Config.class)
class GradePublishAtomicityTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AcademicMutationService service;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TrackingJdbc named;

    @BeforeEach
    void seed() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS notifications");
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"academic\".\"Section\" (\"id\" VARCHAR PRIMARY KEY, \"semesterId\" VARCHAR, \"capacity\" INT, \"enrolledCount\" INT, \"status\" VARCHAR)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"academic\".\"Student\" (\"id\" VARCHAR PRIMARY KEY, \"userId\" VARCHAR)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"academic\".\"Enrollment\" (\"id\" VARCHAR PRIMARY KEY, \"studentId\" VARCHAR, \"sectionId\" VARCHAR, \"status\" VARCHAR, \"gradeStatus\" VARCHAR, \"finalGrade\" DECIMAL, \"letterGrade\" VARCHAR, \"updatedAt\" TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"academic\".\"GradeItem\" (\"id\" VARCHAR PRIMARY KEY, \"type\" VARCHAR, \"sectionId\" VARCHAR, \"name\" VARCHAR, \"maxScore\" DECIMAL, \"weight\" DECIMAL, \"gradedAt\" TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"academic\".\"StudentGrade\" (\"id\" VARCHAR PRIMARY KEY, \"enrollmentId\" VARCHAR, \"gradeItemId\" VARCHAR, \"score\" DECIMAL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS notifications.notification (id VARCHAR PRIMARY KEY, user_id VARCHAR, title VARCHAR, message VARCHAR, type VARCHAR, link VARCHAR, announcement_id VARCHAR(120), source_key VARCHAR(160), is_read BOOLEAN, read_at TIMESTAMP, created_at TIMESTAMP, updated_at TIMESTAMP)");
        for (String table : new String[]{"StudentGrade", "GradeItem", "Enrollment", "Student", "Section"}) {
            jdbc.execute("DELETE FROM \"academic\".\"" + table + "\"");
        }
        jdbc.execute("DELETE FROM notifications.notification");
        jdbc.execute("INSERT INTO \"academic\".\"Section\" VALUES ('section', 'semester', 30, 1, 'ACTIVE')");
        jdbc.execute("INSERT INTO \"academic\".\"Student\" VALUES ('student', 'student-user')");
        jdbc.execute("INSERT INTO \"academic\".\"Enrollment\" VALUES ('enrollment', 'student', 'section', 'ENROLLED', 'DRAFT', 8, 'B+', CURRENT_TIMESTAMP)");
        jdbc.execute("INSERT INTO \"academic\".\"GradeItem\" (\"id\", \"type\") VALUES ('process', 'PROCESS'), ('final', 'FINAL')");
        jdbc.execute("INSERT INTO \"academic\".\"StudentGrade\" VALUES ('p', 'enrollment', 'process', 8), ('f', 'enrollment', 'final', 8)");
    }

    @Test
    void rollbackDoesNotTellStudentThatGradesWerePublished() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.publishGrades("section", null, true, null);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT \"gradeStatus\" FROM \"academic\".\"Enrollment\"", String.class)).isEqualTo("DRAFT");
        assertThat(inboxCount()).isZero();
    }

    @Test
    void commitSendsOnceAndRepeatPublicationDoesNotDuplicate() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.publishGrades("section", null, true, null);
            assertThat(inboxCount()).isZero();
        });
        assertThat(inboxCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT \"gradeStatus\" FROM \"academic\".\"Enrollment\"", String.class)).isEqualTo("PUBLISHED");
        assertThatThrownBy(() -> service.publishGrades("section", null, true, null))
                .hasMessageContaining("No complete grades");
        assertThat(inboxCount()).isEqualTo(1);
    }

    private int inboxCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications.notification", Integer.class);
    }

    @Test
    void gradeSaveWaitsForPublicationThenRejectsThePublishedRecord() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var started = new CountDownLatch(1);
        var waitingSave = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                service.publishGrades("section", null, true, null);
                named.nextSectionRead = started;
                waitingSave.set(executor.submit(() -> service.updateGrades("section", null, true, null,
                        List.of(new GradeUpdate("enrollment", BigDecimal.ONE, BigDecimal.ONE)))));
                try {
                    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                    // The write reached the section lock while the publisher
                    // still owns it; it cannot complete until that TX ends.
                    assertThatThrownBy(() -> waitingSave.get().get(150, TimeUnit.MILLISECONDS))
                            .isInstanceOf(TimeoutException.class);
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
            });
            assertThatThrownBy(() -> waitingSave.get().get(5, TimeUnit.SECONDS))
                    .hasRootCauseMessage("Published grades can no longer be edited");
            assertThat(jdbc.queryForObject("SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\"", BigDecimal.class))
                    .isEqualByComparingTo("8");
        } finally {
            named.nextSectionRead = null;
            executor.shutdownNow();
        }
    }

    static class TrackingJdbc extends NamedParameterJdbcTemplate {
        volatile CountDownLatch nextSectionRead;
        TrackingJdbc(JdbcTemplate jdbc) { super(jdbc); }
        @Override public Map<String, Object> queryForMap(String sql, SqlParameterSource params) {
            CountDownLatch signal = nextSectionRead;
            if (signal != null && sql.contains("FOR UPDATE") && sql.contains("\"Section\"")) {
                nextSectionRead = null;
                signal.countDown();
            }
            return super.queryForMap(sql, params);
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:grade_publish_atomicity;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean PlatformTransactionManager transactions(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean TrackingJdbc named(JdbcTemplate jdbc) { return new TrackingJdbc(jdbc); }
        @Bean GradePublishNotifier notifier(NamedParameterJdbcTemplate jdbc) { return new GradePublishNotifier(new NotificationWriteRepository(jdbc)); }
        @Bean AcademicMutationService service(NamedParameterJdbcTemplate jdbc, GradePublishNotifier notifier) {
            return new AcademicMutationService(jdbc, mock(AcademicEnrollmentReadService.class), mock(RegistrationService.class), mock(AdminAuditRecorder.class), notifier);
        }
    }
}
