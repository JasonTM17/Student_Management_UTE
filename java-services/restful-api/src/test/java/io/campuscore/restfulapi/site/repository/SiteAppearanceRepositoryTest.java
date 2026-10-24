package io.campuscore.restfulapi.site.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * Focused coverage of the repository half of the site-appearance split:
 * update-then-insert upsert semantics on the single row and the actor-label
 * column clip, directly against an H2 table with the production DDL shape.
 */
class SiteAppearanceRepositoryTest {

    private JdbcTemplate jdbc;
    private SiteAppearanceRepository repository;

    @BeforeEach
    void prepareTable() {
        SimpleDriverDataSource source = new SimpleDriverDataSource();
        source.setDriverClass(org.h2.Driver.class);
        source.setUrl("jdbc:h2:mem:site_appearance_repo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        repository = new SiteAppearanceRepository(new NamedParameterJdbcTemplate(jdbc));
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"site\"");
        jdbc.execute("DROP TABLE IF EXISTS \"site\".\"Appearance\"");
        jdbc.execute("""
                CREATE TABLE "site"."Appearance" (
                    "id" VARCHAR(40) PRIMARY KEY,
                    "payload" TEXT NOT NULL,
                    "updatedBy" VARCHAR(255),
                    "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT site_appearance_payload_ck CHECK (length("payload") <= 131072)
                )
                """);
    }

    @Test
    @DisplayName("read returns null before anything is saved")
    void readReturnsNullWhenEmpty() {
        assertNull(repository.read());
    }

    @Test
    @DisplayName("upsert inserts the single row, then updates it instead of appending")
    void upsertKeepsASingleRow() {
        repository.upsert("{\"version\":1}", "admin-1");
        repository.upsert("{\"version\":2}", "admin-2");

        List<String> ids = jdbc.queryForList(
                "SELECT \"id\" FROM \"site\".\"Appearance\"", String.class);
        assertEquals(List.of(SiteAppearanceRepository.ROW_ID), ids);
        assertEquals("{\"version\":2}", repository.read());
        assertEquals("admin-2", jdbc.queryForObject(
                "SELECT \"updatedBy\" FROM \"site\".\"Appearance\" WHERE \"id\" = ?",
                String.class, SiteAppearanceRepository.ROW_ID));
    }

    @Test
    @DisplayName("an actor id longer than the column clips instead of failing")
    void actorLabelClipsLongSubjects() {
        repository.upsert("{\"version\":1}", "s".repeat(300));

        String storedActor = jdbc.queryForObject(
                "SELECT \"updatedBy\" FROM \"site\".\"Appearance\" WHERE \"id\" = ?",
                String.class, SiteAppearanceRepository.ROW_ID);
        assertEquals(SiteAppearanceRepository.MAX_UPDATED_BY_CHARS, storedActor.length());
    }
}
