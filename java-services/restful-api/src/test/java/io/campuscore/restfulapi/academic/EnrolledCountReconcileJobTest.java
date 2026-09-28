package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Wave 1.4: drift between academic."Section"."enrolledCount" and the real
 * active-enrollment count must be detected (WARN logged) and, only with the
 * repair switch on, corrected. Drift is injected straight through JDBC on an
 * isolated H2 fixture, mirroring the other persistence tests.
 */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:enrolled_count_reconcile;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class EnrolledCountReconcileJobTest {

    @Autowired
    private JdbcTemplate jdbc;

    private ListAppender<ILoggingEvent> logCapture;
    private Logger jobLogger;

    @BeforeEach
    void prepareFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"academic\".\"Section\" ("
                + "\"id\" VARCHAR(120) PRIMARY KEY, "
                + "\"enrolledCount\" INTEGER NOT NULL DEFAULT 0, "
                + "\"capacity\" INTEGER NOT NULL DEFAULT 100, "
                + "\"updatedAt\" TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"academic\".\"Enrollment\" ("
                + "\"id\" VARCHAR(120) PRIMARY KEY, "
                + "\"studentId\" VARCHAR(120) NOT NULL, "
                + "\"sectionId\" VARCHAR(120) NOT NULL, "
                + "\"status\" VARCHAR(40) NOT NULL)");
        jdbc.update("DELETE FROM \"academic\".\"Enrollment\"");
        jdbc.update("DELETE FROM \"academic\".\"Section\"");

        // section-ok: counter matches (1 active enrollment, no drift).
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"enrolledCount\") VALUES ('section-ok', 1)");
        insertEnrollment("e-ok", "section-ok", "ENROLLED");

        // section-overstated: counter says 3, only 2 active (1 DROPPED).
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"enrolledCount\") VALUES ('section-overstated', 3)");
        insertEnrollment("e-over-1", "section-overstated", "ENROLLED");
        insertEnrollment("e-over-2", "section-overstated", "PENDING");
        insertEnrollment("e-over-3", "section-overstated", "DROPPED");

        // section-understated: counter says 0, 1 active enrollment hidden.
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"enrolledCount\") VALUES ('section-understated', 0)");
        insertEnrollment("e-under-1", "section-understated", "CONFIRMED");

        jobLogger = (Logger) LoggerFactory.getLogger(
                io.campuscore.restfulapi.academic.service.EnrolledCountReconcileJob.class);
        logCapture = new ListAppender<>();
        logCapture.start();
        jobLogger.addAppender(logCapture);
        jobLogger.setLevel(Level.WARN);
    }

    @AfterEach
    void detachCapture() {
        jobLogger.detachAppender(logCapture);
        jobLogger.setLevel(null);
    }

    private void insertEnrollment(String id, String sectionId, String status) {
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\") "
                + "VALUES (?, 'student-' || ?, ?, ?)", id, id, sectionId, status);
    }

    private List<ILoggingEvent> warnEvents() {
        return logCapture.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .toList();
    }

    @Test
    void driftIsDetectedAndLoggedWithoutRepair() {
        io.campuscore.restfulapi.academic.service.EnrolledCountReconcileJob job =
                new io.campuscore.restfulapi.academic.service.EnrolledCountReconcileJob(
                        new NamedParameterJdbcTemplate(jdbc), false);

        job.reconcile();

        List<ILoggingEvent> warnings = warnEvents();
        assertThat(warnings).hasSize(1);
        String message = warnings.get(0).getFormattedMessage();
        assertThat(message).contains("drift on 2 section(s)");
        assertThat(message).contains("section-overstated");
        assertThat(message).contains("section-understated");
        assertThat(message).doesNotContain("section-ok");
        assertThat(message).contains("repair=disabled");

        // Repair is off: the stored counters must stay untouched.
        assertThat(counterOf("section-overstated")).isEqualTo(3);
        assertThat(counterOf("section-understated")).isEqualTo(0);
        assertThat(counterOf("section-ok")).isEqualTo(1);
    }

    @Test
    void repairModeCorrectsCountersToTrueActiveCounts() {
        io.campuscore.restfulapi.academic.service.EnrolledCountReconcileJob job =
                new io.campuscore.restfulapi.academic.service.EnrolledCountReconcileJob(
                        new NamedParameterJdbcTemplate(jdbc), true);

        job.reconcile();

        List<ILoggingEvent> warnings = warnEvents();
        assertThat(warnings).hasSize(2);
        assertThat(warnings.get(0).getFormattedMessage()).contains("repair=auto-repairing");
        assertThat(warnings.get(1).getFormattedMessage()).contains("corrected 2 section counter(s)");

        assertThat(counterOf("section-overstated")).isEqualTo(2);
        assertThat(counterOf("section-understated")).isEqualTo(1);
        assertThat(counterOf("section-ok")).isEqualTo(1);

        // A repaired state is drift-free on the next pass.
        logCapture.list.clear();
        job.reconcile();
        assertThat(warnEvents()).isEmpty();
    }

    private int counterOf(String sectionId) {
        Integer count = jdbc.queryForObject(
                "SELECT \"enrolledCount\" FROM \"academic\".\"Section\" WHERE \"id\" = ?",
                Integer.class, sectionId);
        return count == null ? -1 : count;
    }
}
