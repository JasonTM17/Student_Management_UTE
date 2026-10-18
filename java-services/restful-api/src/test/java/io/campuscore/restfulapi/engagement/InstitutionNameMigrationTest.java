package io.campuscore.restfulapi.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class InstitutionNameMigrationTest {
    private static final String NAME = "Trường Đại học Công nghệ kĩ thuật TP.HCM";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void correctsFullNamesAndRetainsPublicationMetadataAndPriorAudits() throws Exception {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:name-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        try (Connection connection = dataSource.getConnection()) {
            var jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
            jdbc.execute("CREATE SCHEMA engagement");
            jdbc.execute("CREATE TABLE engagement.\"Announcement\" (id varchar(120) PRIMARY KEY,title text,content text,"
                    + "\"publishedBy\" text,version int NOT NULL,\"updatedAt\" timestamp,"
                    + "priority varchar(20),\"targetRoles\" varchar(80),\"archivedAt\" timestamp,"
                    + "\"notifiedAt\" timestamp,slug varchar(120))");
            jdbc.execute("CREATE TABLE engagement.\"AnnouncementAudit\" (id varchar(120) PRIMARY KEY,"
                    + "\"announcementId\" varchar(120),action varchar(32),\"actorId\" varchar(120),"
                    + "\"actorLabel\" varchar(240),reason varchar(500),version int,"
                    + "\"beforeState\" text,\"afterState\" text,\"createdAt\" timestamp)");
            String[] variants = {
                "Trường Đại học Sư phạm Kỹ thuật TP. Hồ Chí Minh",
                "ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT THÀNH PHỐ HỒ CHÍ MINH",
                "Trường ĐH Công nghệ Kỹ thuật TP.HCM"
            };
            for (int i = 0; i < variants.length; i++) {
                jdbc.update("INSERT INTO engagement.\"Announcement\" VALUES (?,?,?, ?,7,TIMESTAMP '2026-01-01 00:00:00',"
                                + "'HIGH','STUDENT',TIMESTAMP '2026-02-01 00:00:00',TIMESTAMP '2026-01-02 00:00:00','retained-slug')",
                        "changed-" + i, variants[i], "<p>" + variants[i] + " / " + variants[i] + "</p>", "Office - " + variants[i]);
            }
            jdbc.update("INSERT INTO engagement.\"Announcement\" (id,title,content,version) VALUES ('current',?,?,4)", NAME, "Already current");
            jdbc.update("INSERT INTO engagement.\"Announcement\" (id,title,content,version) VALUES ('other',?,?,5)",
                    "Trường Đại học Sư phạm Kỹ thuật Vĩnh Long", "Other institution and unchanged URL https://example.invalid/a");
            jdbc.update("INSERT INTO engagement.\"Announcement\" (id,title,content,version) VALUES ('nullable',NULL,NULL,2)");
            jdbc.update("INSERT INTO engagement.\"AnnouncementAudit\" (id,\"announcementId\",action,reason,version)"
                    + " VALUES ('prior','changed-0','CREATED','Retained history',0)");
            Map<String, Object> original = jdbc.queryForMap("SELECT * FROM engagement.\"Announcement\" WHERE id='changed-0'");
            var unchanged = jdbc.queryForList("SELECT * FROM engagement.\"Announcement\" WHERE id IN ('current','other','nullable') ORDER BY id");

            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration-h2/V122__standardize_current_institution_name.sql"));

            assertThat(jdbc.queryForObject("SELECT count(*) FROM engagement.\"Announcement\"", Integer.class)).isEqualTo(6);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM engagement.\"AnnouncementAudit\"", Integer.class)).isEqualTo(4);
            for (int i = 0; i < variants.length; i++) {
                Map<String, Object> row = jdbc.queryForMap("SELECT * FROM engagement.\"Announcement\" WHERE id=?", "changed-" + i);
                assertThat(row.get("title")).isEqualTo(NAME);
                assertThat(row.get("content")).isEqualTo("<p>" + NAME + " / " + NAME + "</p>");
                assertThat(row.get("publishedBy")).isEqualTo("Office - " + NAME);
                assertThat(row.get("version")).isEqualTo(8);
                for (String field : new String[]{"priority","targetRoles","archivedAt","notifiedAt","slug"}) {
                    assertThat(row.get(field)).as(field).isEqualTo(original.get(field));
                }
                Map<String, Object> audit = jdbc.queryForMap("SELECT * FROM engagement.\"AnnouncementAudit\" WHERE \"announcementId\"=? AND action='UPDATED'", "changed-" + i);
                assertThat(audit.get("actorId")).isEqualTo("system-migration");
                assertThat(mapper.readTree((String) audit.get("beforeState")).get("title").asText()).isEqualTo(variants[i]);
                assertThat(mapper.readTree((String) audit.get("afterState")).get("title").asText()).isEqualTo(NAME);
            }
            assertThat(jdbc.queryForList("SELECT * FROM engagement.\"Announcement\" WHERE id IN ('current','other','nullable') ORDER BY id")).isEqualTo(unchanged);
            assertThat(jdbc.queryForObject("SELECT reason FROM engagement.\"AnnouncementAudit\" WHERE id='prior'", String.class)).isEqualTo("Retained history");
            var after = jdbc.queryForList("SELECT * FROM engagement.\"Announcement\" ORDER BY id");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration-h2/V122__standardize_current_institution_name.sql"));
            assertThat(jdbc.queryForList("SELECT * FROM engagement.\"Announcement\" ORDER BY id")).isEqualTo(after);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM engagement.\"AnnouncementAudit\"", Integer.class)).isEqualTo(4);
        }
    }
}
