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
 * Migration validation test for Flyway V41:
 * V41__enrich_comprehensive_academic_knowledge.sql
 *
 * Verifies that the comprehensive academic knowledge enrichment migration is well-formed, idempotent,
 * covers all required campus and academic life topics (student conduct scores ĐRL, exam regulations and absenteeism,
 * leaves of absence / result deferment, GPA/CPA calculation and grading scales, student affairs one-stop services and BHYT,
 * and exam regrading appeals), and accurately configures the V41 release projection and runtime state pointer.
 */
class ComprehensiveAcademicKnowledgeEnrichmentMigrationTest {

    private static final Path MIGRATION_PATH =
            Path.of("src/main/resources/db/migration/V41__enrich_comprehensive_academic_knowledge.sql");
    private static String sql;

    @BeforeAll
    static void loadMigrationSql() throws Exception {
        assertThat(MIGRATION_PATH)
                .as("Flyway migration V41 file must exist at expected path")
                .exists();
        sql = Files.readString(MIGRATION_PATH);
        assertThat(sql).isNotBlank();
    }

    @Test
    @DisplayName("V41 script follows naming convention and targets assistant schema")
    void v41FollowsNamingAndSchemaConventions() {
        assertThat(MIGRATION_PATH.getFileName().toString())
                .isEqualTo("V41__enrich_comprehensive_academic_knowledge.sql")
                .startsWith("V41__");

        assertThat(sql)
                .contains("INSERT INTO assistant.knowledge_document")
                .contains("INSERT INTO assistant.knowledge_document_revision")
                .contains("INSERT INTO assistant.knowledge_release")
                .contains("INSERT INTO assistant.knowledge_runtime_document")
                .contains("INSERT INTO assistant.knowledge_runtime_state");
    }

    @Test
    @DisplayName("V41 contains all 12 required document slugs (6 topics x 2 locales)")
    void v41DeclaresAllRequiredSlugs() {
        List<String> expectedSlugs = List.of(
                "student-conduct-score-handbook-vi",
                "student-conduct-score-handbook-en",
                "exam-regulations-and-absenteeism-vi",
                "exam-regulations-and-absenteeism-en",
                "leave-of-absence-and-deferment-vi",
                "leave-of-absence-and-deferment-en",
                "curriculum-gpa-weighting-and-credit-system-vi",
                "curriculum-gpa-weighting-and-credit-system-en",
                "student-affairs-services-and-certificates-vi",
                "student-affairs-services-and-certificates-en",
                "academic-appeals-and-re-evaluation-vi",
                "academic-appeals-and-re-evaluation-en");

        for (String slug : expectedSlugs) {
            assertThat(sql)
                    .as("Migration must seed document slug: %s", slug)
                    .contains("'" + slug + "'");
        }
    }

    @Test
    @DisplayName("V41 assigns valid domains and source provenance to all seeded documents")
    void v41AssignsValidDomainsAndProvenance() {
        assertThat(sql).contains("'campuscore-academic-enrichment'");

        // Policy and Academic Catalog domains
        assertThat(sql).contains("'POLICY'");
        assertThat(sql).contains("'ACADEMIC_CATALOG'");

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
            assertThat(domain).isIn("POLICY", "ACADEMIC_CATALOG");

            if (slug.endsWith("-vi")) {
                assertThat(locale).isEqualTo("vi");
            } else if (slug.endsWith("-en")) {
                assertThat(locale).isEqualTo("en");
            }
        }
        assertThat(count).isEqualTo(12);
    }

    @Test
    @DisplayName("Topic 1: Student conduct and training points handbook is comprehensively covered")
    void v41CoversStudentConductPoints() {
        assertThat(sql).contains("Quy chế đánh giá Điểm rèn luyện sinh viên");
        assertThat(sql).contains("thang điểm 100");
        assertThat(sql).contains("Ý thức tham gia học tập");
        assertThat(sql).contains("Xuất sắc (90-100)");
        assertThat(sql).contains("Điểm rèn luyện là căn cứ xét học bổng");

        assertThat(sql).contains("Student conduct and training points evaluation regulations");
        assertThat(sql).contains("100-point scale");
    }

    @Test
    @DisplayName("Topic 2: Examination regulations, attendance minimums, and makeup exams are covered")
    void v41CoversExamRegulationsAndMakeupExams() {
        assertThat(sql).contains("Quy chế thi kết thúc học phần và xử lý vắng thi");
        assertThat(sql).contains("tối thiểu 80% số tiết học lý thuyết");
        assertThat(sql).contains("vắng quá 20% số tiết học phần sẽ bị cấm thi");
        assertThat(sql).contains("trong vòng 7 ngày làm việc");
        assertThat(sql).contains("thi bù ở đợt thi gần nhất");

        assertThat(sql).contains("End-of-course examination regulations and absenteeism policy");
        assertThat(sql).contains("exceeding 20% absences results in an exam ban");
    }

    @Test
    @DisplayName("Topic 3: Leave of absence, result deferment, and military service rules are covered")
    void v41CoversLeaveOfAbsenceAndDeferment() {
        assertThat(sql).contains("Quy chế tạm dừng học tập, bảo lưu kết quả và thôi học");
        assertThat(sql).contains("Đã học ít nhất một học kỳ chính");
        assertThat(sql).contains("không quá 2 học kỳ chính");
        assertThat(sql).contains("nghĩa vụ quân sự");
        assertThat(sql).contains("Ít nhất 2 tuần trước khi bắt đầu học kỳ mới");

        assertThat(sql).contains("Regulations on temporary absence, result deferment and academic withdrawal");
        assertThat(sql).contains("Military service leave");
    }

    @Test
    @DisplayName("Topic 4: Course component weights, GPA/CPA calculation, and 4.0 conversions are covered")
    void v41CoversGradeWeightsAndGpaCalculation() {
        assertThat(sql).contains("Cơ cấu điểm học phần, cách tính GPA và hệ thống tín chỉ");
        assertThat(sql).contains("Điểm quá trình (chiếm 50%");
        assertThat(sql).contains("Điểm thi kết thúc học phần (chiếm 50%)");
        assertThat(sql).contains("8.5-10.0 tương ứng loại A (thang 4: 4.0)");
        assertThat(sql).contains("dưới 4.0 là loại F (0.0 - không đạt)");
        assertThat(sql).contains("tổng tích số giữa điểm số thang 4 của từng môn với số tín chỉ");

        assertThat(sql).contains("Course component weights, GPA calculation and credit system");
        assertThat(sql).contains("Semester GPA and Cumulative GPA (CPA)");
    }

    @Test
    @DisplayName("Topic 5: One-stop student services, certificates, and BHYT health insurance are covered")
    void v41CoversStudentServicesAndInsurance() {
        assertThat(sql).contains("Dịch vụ hành chính một cửa sinh viên, cấp giấy tờ và BHYT");
        assertThat(sql).contains("Bộ phận Một cửa - Phòng Công tác Sinh viên");
        assertThat(sql).contains("Giấy xác nhận đang là sinh viên");
        assertThat(sql).contains("Cấp lại thẻ sinh viên");
        assertThat(sql).contains("Bảo hiểm y tế (BHYT) bắt buộc");

        assertThat(sql).contains("One-stop student affairs services, certificates and health insurance");
        assertThat(sql).contains("Mandatory Health Insurance (BHYT)");
    }

    @Test
    @DisplayName("Topic 6: Grade appeals and exam re-evaluation procedures are covered")
    void v41CoversGradeAppealsProcedure() {
        assertThat(sql).contains("Quy trình khiếu nại và phúc khảo điểm thi học phần");
        assertThat(sql).contains("Trong vòng 7 ngày làm việc");
        assertThat(sql).contains("Phòng Khảo thí và Đảm bảo chất lượng");
        assertThat(sql).contains("chênh lệch từ 0.5 điểm trở lên");

        assertThat(sql).contains("Grade appeal and exam re-evaluation procedure");
        assertThat(sql).contains("Testing and Quality Assurance Office");
    }

    @Test
    @DisplayName("V41 activates release 00000000-0000-0000-0000-000000000041 and updates runtime state")
    void v41ConfiguresReleaseAndRuntimeState() {
        String expectedReleaseId = "00000000-0000-0000-0000-000000000041";

        assertThat(sql).contains("'" + expectedReleaseId + "'::uuid");
        assertThat(sql).contains("'local-demo-v41'");
        assertThat(sql).contains("'PUBLISHED'");
        assertThat(sql).contains("assistant.knowledge_runtime_document");
        assertThat(sql).contains("assistant.knowledge_runtime_state (singleton, active_release_id)");
        assertThat(sql).contains("ON CONFLICT (singleton) DO UPDATE");
        assertThat(sql).contains("SET active_release_id = EXCLUDED.active_release_id");
    }

    @Test
    @DisplayName("V41 statements are idempotent with WHERE NOT EXISTS guards")
    void v41IsIdempotent() {
        assertThat(sql).contains("WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document existing WHERE existing.slug = seed.slug)");
        assertThat(sql).contains("WHERE d.source = 'campuscore-academic-enrichment'");
        assertThat(sql).contains("AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_document_revision r WHERE r.document_id = d.id AND r.version = 1)");
        assertThat(sql).contains("WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000041'::uuid)");
        assertThat(sql).contains("WHERE p.release_id = '00000000-0000-0000-0000-000000000041'::uuid AND p.source_id = d.id::text");
    }

    @Test
    @DisplayName("V41 script has balanced parentheses and valid SQL terminator statements")
    void v41HasBalancedSyntax() {
        int openParen = 0;
        int closeParen = 0;
        boolean inSingleQuote = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    i++;
                } else {
                    inSingleQuote = !inSingleQuote;
                }
            } else if (!inSingleQuote) {
                if (c == '(') {
                    openParen++;
                } else if (c == ')') {
                    closeParen++;
                }
            }
        }

        assertThat(inSingleQuote)
                .as("Single quotes should be closed properly")
                .isFalse();
        assertThat(openParen)
                .as("Parentheses should be balanced")
                .isEqualTo(closeParen);
    }
}
