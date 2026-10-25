package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

/**
 * H2 persistence coverage for V150__grade_published_at_gap_backfill: the
 * replayed migration stamps the publication instant on official grade rows
 * that were written without it, leaves withdrawn W records NULL, and is
 * idempotent so a retry never moves an appeal deadline.
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:grade_published_at_backfill;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"test", "persistence"})
class GradePublishedAtBackfillPersistenceTest {

    private static final String PREFIX = "gpab-";
    private static final Instant KEPT_AT = Instant.parse("2026-10-20T09:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private javax.sql.DataSource dataSource;

    @BeforeEach
    void fixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        jdbc.execute("DELETE FROM academic.\"Enrollment\" WHERE \"id\" LIKE 'gpab-%'");
        insert("gpab-completed-null", "COMPLETED", "PUBLISHED", "B", null);
        insert("gpab-appealed-null", "COMPLETED", "APPEALED", "A", null);
        insert("gpab-withdrawn-null", "WITHDRAWN", "PUBLISHED", "W", null);
        insert("gpab-keep-timestamp", "COMPLETED", "PUBLISHED", "B+", Timestamp.from(KEPT_AT));
        insert("gpab-draft-null", "ENROLLED", "DRAFT", null, null);
    }

    @Test
    void backfillStampsOfficialNonWithdrawnRowsAndSkipsWithdrawnAndUnofficial() {
        applyV150();

        Map<String, Object> rows = jdbc.queryForMap(
                "SELECT COUNT(*) FILTER (WHERE \"gradePublishedAt\" IS NULL) AS nulls,"
                        + " COUNT(*) FILTER (WHERE \"gradePublishedAt\" IS NOT NULL) AS stamped"
                        + " FROM academic.\"Enrollment\" WHERE \"id\" LIKE 'gpab-%'");
        assertThat(rows.get("stamped")).isEqualTo(3L);
        assertThat(rows.get("nulls")).isEqualTo(2L);

        assertThat(publishedAt("gpab-completed-null")).isNotNull();
        assertThat(publishedAt("gpab-appealed-null")).isNotNull();
        assertThat(publishedAt("gpab-keep-timestamp")).isEqualTo(Timestamp.from(KEPT_AT));
        assertThat(publishedAt("gpab-withdrawn-null")).isNull();
        assertThat(publishedAt("gpab-draft-null")).isNull();
    }

    @Test
    void backfillIsIdempotentAcrossReplays() {
        applyV150();
        Timestamp stamped = publishedAt("gpab-completed-null");
        assertThat(stamped).isNotNull();

        applyV150();
        assertThat(publishedAt("gpab-completed-null")).isEqualTo(stamped);
        assertThat(publishedAt("gpab-appealed-null")).isNotNull();
        assertThat(publishedAt("gpab-withdrawn-null")).isNull();
    }

    private void applyV150() {
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration-h2/V150__grade_published_at_gap_backfill.sql")).execute(dataSource);
    }

    private void insert(String id, String status, String gradeStatus, String letter, Timestamp publishedAt) {
        jdbc.update("INSERT INTO academic.\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\","
                        + " \"gradeStatus\", \"letterGrade\", \"gradePublishedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?)",
                id, PREFIX + "student", PREFIX + "section", PREFIX + "semester", status, gradeStatus, letter,
                publishedAt);
    }

    private Timestamp publishedAt(String id) {
        return jdbc.queryForObject(
                "SELECT \"gradePublishedAt\" FROM academic.\"Enrollment\" WHERE \"id\" = ?",
                Timestamp.class, id);
    }
}
