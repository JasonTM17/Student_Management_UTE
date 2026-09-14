package io.campuscore.restfulapi.thesis.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Migration validation test for Flyway V42:
 * V42__enrich_campus_life_and_internship_knowledge.sql
 *
 * Verifies that the campus life, graduation internship, thesis formatting, central library,
 * IT services, and student research knowledge migration is well-formed, idempotent,
 * and accurately configures the V42 release projection and runtime state pointer.
 */
class CampusLifeAndInternshipKnowledgeMigrationTest {

    private static final Path MIGRATION_PATH =
            Path.of("src/main/resources/db/migration/V42__enrich_campus_life_and_internship_knowledge.sql");
    private static String sql;

    @BeforeAll
    static void loadMigrationSql() throws Exception {
        assertThat(MIGRATION_PATH)
                .as("Flyway migration V42 file must exist at expected path")
                .exists();
        sql = Files.readString(MIGRATION_PATH);
        assertThat(sql).isNotBlank();
    }

    @Test
    @DisplayName("V42 script follows naming convention and targets assistant schema")
    void v42FollowsNamingAndSchemaConventions() {
        assertThat(MIGRATION_PATH.getFileName().toString())
                .isEqualTo("V42__enrich_campus_life_and_internship_knowledge.sql")
                .startsWith("V42__");

        assertThat(sql)
                .contains("INSERT INTO assistant.knowledge_document")
                .contains("INSERT INTO assistant.knowledge_document_revision")
                .contains("INSERT INTO assistant.knowledge_release")
                .contains("INSERT INTO assistant.knowledge_runtime_document")
                .contains("INSERT INTO assistant.knowledge_runtime_state");
    }

    @Test
    @DisplayName("V42 contains all 12 required document slugs (6 topics x 2 locales)")
    void v42DeclaresAllRequiredSlugs() {
        List<String> expectedSlugs = List.of(
                "internship-enterprise-regulations-vi",
                "internship-enterprise-regulations-en",
                "thesis-formatting-and-submission-guide-vi",
                "thesis-formatting-and-submission-guide-en",
                "library-services-and-digital-resources-vi",
                "library-services-and-digital-resources-en",
                "dormitory-regulations-and-security-vi",
                "dormitory-regulations-and-security-en",
                "campus-it-services-and-wifi-vi",
                "campus-it-services-and-wifi-en",
                "student-scientific-research-and-awards-vi",
                "student-scientific-research-and-awards-en");

        for (String slug : expectedSlugs) {
            assertThat(sql)
                    .as("Migration must seed document slug: %s", slug)
                    .contains("'" + slug + "'");
        }
    }

    @Test
    @DisplayName("V42 assigns valid domains and source provenance to all seeded documents")
    void v42AssignsValidDomainsAndProvenance() {
        assertThat(sql).contains("'campuscore-campus-life-enrichment'");

        Pattern valuesRowPattern = Pattern.compile(
                "\\('([a-z0-9-]+)',\\s*'([a-z]{2})',\\s*'([^']+)',\\s*'([^']+)',\\s*(\\d+),\\s*'([A-Z_]+)'\\)");
        Matcher matcher = valuesRowPattern.matcher(sql);

        int count = 0;
        while (matcher.find()) {
            count++;
            String slug = matcher.group(1);
            String locale = matcher.group(2);
            String title = matcher.group(3);
            String content = matcher.group(4);
            int priority = Integer.parseInt(matcher.group(5));
            String domain = matcher.group(6);

            assertThat(locale).isIn("vi", "en");
            assertThat(title).isNotBlank();
            assertThat(content.length()).isGreaterThan(100);
            assertThat(priority).isBetween(1, 100);
            assertThat(domain).isIn("POLICY", "THESIS", "GENERAL_FAQ");

            if (slug.endsWith("-vi")) {
                assertThat(locale).isEqualTo("vi");
            } else if (slug.endsWith("-en")) {
                assertThat(locale).isEqualTo("en");
            }
        }
        assertThat(count).isEqualTo(12);
    }

    @Test
    @DisplayName("Topic 1: Internship regulations and partner enterprises are covered")
    void v42CoversInternships() {
        assertThat(sql).contains("Quy chế thực tập tốt nghiệp và liên kết doanh nghiệp");
        assertThat(sql).contains("tối thiểu 100 tín chỉ");
        assertThat(sql).contains("8 đến 12 tuần");

        assertThat(sql).contains("Graduation internship regulations and enterprise collaboration");
        assertThat(sql).contains("8 to 12 weeks");
    }

    @Test
    @DisplayName("Topic 2: Thesis formatting, hardcopy submission, and defense protocol are covered")
    void v42CoversThesisFormatting() {
        assertThat(sql).contains("Quy chuẩn trình bày, nộp quyển và bảo vệ khóa luận tốt nghiệp");
        assertThat(sql).contains("khổ A4 một mặt, bìa cứng màu xanh dương in chữ nhũ vàng");
        assertThat(sql).contains("chuẩn IEEE");
        assertThat(sql).contains("nộp 03 quyển in");

        assertThat(sql).contains("Thesis formatting, submission standards, and defense protocol");
        assertThat(sql).contains("Times New Roman font size 13");
    }

    @Test
    @DisplayName("Topic 3: Central library and digital research repositories are covered")
    void v42CoversLibraryServices() {
        assertThat(sql).contains("Quy định Thư viện trung tâm và tài nguyên học liệu số");
        assertThat(sql).contains("mượn tối đa 05 đầu sách");
        assertThat(sql).contains("IEEE Xplore, ScienceDirect, SpringerLink");

        assertThat(sql).contains("Central library rules and digital learning resources");
    }

    @Test
    @DisplayName("Topic 4: Dormitory regulations and residential curfew are covered")
    void v42CoversDormitoryRules() {
        assertThat(sql).contains("Quy chế ký túc xá và an ninh trật tự nội trú");
        assertThat(sql).contains("05h00 và đóng cửa vào lúc 23h00");

        assertThat(sql).contains("Dormitory regulations and residential hall security");
    }

    @Test
    @DisplayName("Topic 5: Campus IT services, student email, and Wi-Fi networks are covered")
    void v42CoversCampusItServices() {
        assertThat(sql).contains("Dịch vụ Công nghệ thông tin, Email sinh viên và mạng Wi-Fi");
        assertThat(sql).contains("Eduroam");
        assertThat(sql).contains("Microsoft 365");

        assertThat(sql).contains("Campus IT services, student email, credentials, and Wi-Fi networks");
    }

    @Test
    @DisplayName("Topic 6: Student research awards and scientific incentives are covered")
    void v42CoversStudentResearch() {
        assertThat(sql).contains("Quy định nghiên cứu khoa học sinh viên và khen thưởng học thuật");
        assertThat(sql).contains("Eurékha");
        assertThat(sql).contains("Scopus/WoS");

        assertThat(sql).contains("Student scientific research policies and academic achievement awards");
    }

    @Test
    @DisplayName("V42 activates release 00000000-0000-0000-0000-000000000042 and updates runtime state")
    void v42ConfiguresReleaseAndRuntimeState() {
        String expectedReleaseId = "00000000-0000-0000-0000-000000000042";

        assertThat(sql).contains("'" + expectedReleaseId + "'::uuid");
        assertThat(sql).contains("'local-demo-v42'");
        assertThat(sql).contains("'PUBLISHED'");
        assertThat(sql).contains("assistant.knowledge_runtime_document");
        assertThat(sql).contains("assistant.knowledge_runtime_state (singleton, active_release_id)");
        assertThat(sql).contains("ON CONFLICT (singleton) DO UPDATE");
        assertThat(sql).contains("SET active_release_id = EXCLUDED.active_release_id");
    }

    @Test
    @DisplayName("V42 statements are idempotent with WHERE NOT EXISTS guards")
    void v42IsIdempotent() {
        assertThat(sql).contains("WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document existing WHERE existing.slug = seed.slug)");
        assertThat(sql).contains("WHERE d.source = 'campuscore-campus-life-enrichment'");
        assertThat(sql).contains("AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_document_revision r WHERE r.document_id = d.id AND r.version = 1)");
        assertThat(sql).contains("WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000042'::uuid)");
        assertThat(sql).contains("WHERE p.release_id = '00000000-0000-0000-0000-000000000042'::uuid AND p.source_id = d.id::text");
    }
}
