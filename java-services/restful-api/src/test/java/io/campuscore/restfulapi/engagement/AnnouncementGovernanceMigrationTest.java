package io.campuscore.restfulapi.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Static guard for the additive announcement governance migration contract. */
class AnnouncementGovernanceMigrationTest {

    @Test
    void v15AddsVersionArchiveAndAppendOnlyAuditShape() throws Exception {
        Path script = Path.of("src/main/resources/db/migration/V15__announcement_governance_audit.sql");
        String sql = Files.readString(script);

        assertThat(script).exists();
        assertThat(sql)
                .contains("ADD COLUMN IF NOT EXISTS \"version\" INTEGER NOT NULL DEFAULT 0")
                .contains("ADD COLUMN IF NOT EXISTS \"archivedAt\" TIMESTAMPTZ")
                .contains("ADD COLUMN IF NOT EXISTS \"archivedBy\" VARCHAR(120)")
                .contains("CREATE TABLE IF NOT EXISTS engagement.\"AnnouncementAudit\"")
                .contains("\"actorLabel\" VARCHAR(240)")
                .contains("\"beforeState\" TEXT")
                .contains("\"afterState\" TEXT")
                .contains("CHECK (\"action\" IN ('CREATED', 'UPDATED', 'ARCHIVED', 'RESTORED'))")
                .contains("CHECK (length(trim(\"reason\")) BETWEEN 1 AND 500)")
                .contains("ON DELETE RESTRICT")
                .contains("engagement_announcement_audit_lookup_idx");
    }

    @Test
    void v64AddsArticleEcosystemAndStrictConstraints() throws Exception {
        Path script = Path.of("src/main/resources/db/migration/V64__enterprise_article_ecosystem_and_strict_constraints.sql");
        String sql = Files.readString(script);

        assertThat(script).exists();
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS engagement.\"ArticleCategory\"")
                .contains("CREATE TABLE IF NOT EXISTS engagement.\"ArticleTag\"")
                .contains("CREATE TABLE IF NOT EXISTS engagement.\"AnnouncementTagMap\"")
                .contains("CREATE TABLE IF NOT EXISTS engagement.\"ArticleMediaGallery\"")
                .contains("CREATE TABLE IF NOT EXISTS engagement.\"ArticleAttachment\"")
                .contains("CREATE TABLE IF NOT EXISTS engagement.\"ArticleReadingMetric\"")
                .contains("article_gallery_single_cover_idx")
                .contains("announcement_category_fk")
                .contains("announcement_priority_ck")
                .contains("announcement_status_ck")
                .contains("announcement_date_window_ck")
                .contains("announcement_metrics_ck")
                .contains("announcement-semiconductor-cleanroom")
                .contains("announcement-robotics-iot-lab")
                .contains("announcement-green-summer-volunteer")
                .contains("announcement-campus-sports-cup")
                .contains("announcement-cultural-arts-gala")
                .contains("announcement-stem-conference-keynote")
                .contains("announcement-ai-hackathon-arena")
                .contains("announcement-digital-library-hub")
                .contains("announcement-commencement-graduation")
                .contains("announcement-blood-donation-day")
                .contains("announcement-student-dormitory-campus")
                .contains("announcement-faculty-excellence-awards");
    }
}
