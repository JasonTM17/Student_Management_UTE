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
 * Migration validation test for Flyway V40:
 * V40__seed_academic_regulations_knowledge.sql
 *
 * Verifies that the academic regulations migration is well-formed, idempotent,
 * covers all required academic topics (prerequisites vs prior courses vs corequisites,
 * Grade F retakes and improvements, tuition rules and deadlines, academic probation levels
 * and thesis eligibility, course withdrawal and credit limits, scholarships and graduation requirements),
 * and accurately configures the V16+ release projection and runtime state pointer.
 */
class AcademicRegulationsKnowledgeMigrationTest {

    private static final Path MIGRATION_PATH =
            Path.of("src/main/resources/db/migration/V40__seed_academic_regulations_knowledge.sql");
    private static String sql;

    @BeforeAll
    static void loadMigrationSql() throws Exception {
        assertThat(MIGRATION_PATH)
                .as("Flyway migration V40 file must exist at expected path")
                .exists();
        sql = Files.readString(MIGRATION_PATH);
        assertThat(sql).isNotBlank();
    }

    @Test
    @DisplayName("V40 script follows naming convention and targets assistant schema")
    void v40FollowsNamingAndSchemaConventions() {
        assertThat(MIGRATION_PATH.getFileName().toString())
                .isEqualTo("V40__seed_academic_regulations_knowledge.sql")
                .startsWith("V40__");

        assertThat(sql)
                .contains("INSERT INTO assistant.knowledge_document")
                .contains("INSERT INTO assistant.knowledge_document_revision")
                .contains("INSERT INTO assistant.knowledge_release")
                .contains("INSERT INTO assistant.knowledge_runtime_document")
                .contains("INSERT INTO assistant.knowledge_runtime_state");
    }

    @Test
    @DisplayName("V40 contains all 12 required document slugs (6 topics x 2 locales)")
    void v40DeclaresAllRequiredSlugs() {
        List<String> expectedSlugs = List.of(
                "prerequisite-prior-corequisite-vi",
                "prerequisite-prior-corequisite-en",
                "grade-f-retake-improvement-vi",
                "grade-f-retake-improvement-en",
                "tuition-payment-deadline-rules-vi",
                "tuition-payment-deadline-rules-en",
                "academic-probation-and-thesis-eligibility-vi",
                "academic-probation-and-thesis-eligibility-en",
                "withdrawal-credit-limits-vi",
                "withdrawal-credit-limits-en",
                "scholarships-graduation-requirements-vi",
                "scholarships-graduation-requirements-en");

        for (String slug : expectedSlugs) {
            assertThat(sql)
                    .as("Migration must seed document slug: %s", slug)
                    .contains("'" + slug + "'");
        }
    }

    @Test
    @DisplayName("V40 assigns valid domains and source provenance to all seeded documents")
    void v40AssignsValidDomainsAndProvenance() {
        assertThat(sql).contains("'campuscore-academic-regulations'");

        // Registration domain items
        assertThat(sql).contains("'REGISTRATION'");
        // Policy domain items
        assertThat(sql).contains("'POLICY'");

        // Validate values pattern: ('slug', 'locale', 'title', 'content', priority, 'domain')
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
            assertThat(domain).isIn("REGISTRATION", "POLICY");

            if (slug.endsWith("-vi")) {
                assertThat(locale).isEqualTo("vi");
            } else if (slug.endsWith("-en")) {
                assertThat(locale).isEqualTo("en");
            }
        }
        assertThat(count).isEqualTo(12);
    }

    @Test
    @DisplayName("Topic 1: Prerequisite vs Prior Course vs Corequisite is comprehensively covered")
    void v40CoversPrerequisitesPriorAndCorequisites() {
        // Vietnamese content assertions
        assertThat(sql).contains("Học phần tiên quyết (Prerequisite)");
        assertThat(sql).contains("Học phần học trước (Prior course)");
        assertThat(sql).contains("Học phần song hành (Corequisite)");
        assertThat(sql).contains("tự động chặn");
        assertThat(sql).contains("kể cả nhận điểm F");

        // English content assertions
        assertThat(sql).contains("Prerequisite, prior, and corequisite courses");
        assertThat(sql).contains("passing is not required, even grade F is eligible");
        assertThat(sql).contains("Review the curriculum tree on the CampusCore Portal");
    }

    @Test
    @DisplayName("Topic 2: Grade F handling, course retakes, and grade improvement are covered")
    void v40CoversGradeFRetakeAndImprovement() {
        // Vietnamese content assertions
        assertThat(sql).contains("Quy định xử lý điểm F, học lại và học cải thiện điểm");
        assertThat(sql).contains("Bị điểm F (Học lại)");
        assertThat(sql).contains("Học cải thiện điểm");
        assertThat(sql).contains("Các môn có điểm từ C trở lên không được phép học cải thiện");
        assertThat(sql).contains("thay thế trọng số của lần học trước");

        // English content assertions
        assertThat(sql).contains("Regulations on F grade, course retakes, and grade improvement");
        assertThat(sql).contains("Courses with grade C or higher cannot be repeated for improvement");
        assertThat(sql).contains("replaces the previous attempt");
    }

    @Test
    @DisplayName("Topic 3: Tuition payment deadlines, payment methods, deferrals, and debt handling are covered")
    void v40CoversTuitionDeadlinesMethodsAndDebt() {
        // Vietnamese content assertions
        assertThat(sql).contains("Quy định và thời hạn đóng học phí học kỳ");
        assertThat(sql).contains("trong vòng 4 tuần đầu tiên");
        assertThat(sql).contains("VietQR");
        assertThat(sql).contains("Phòng Kế hoạch - Tài chính");
        assertThat(sql).contains("Gia hạn học phí");
        assertThat(sql).contains("tối đa không quá 4 tuần tiếp theo");
        assertThat(sql).contains("Xử lý nợ học phí");
        assertThat(sql).contains("không có tên trong danh sách thi kết thúc học phần");

        // English content assertions
        assertThat(sql).contains("Semester tuition payment deadlines and financial rules");
        assertThat(sql).contains("first 4 weeks of the semester");
        assertThat(sql).contains("Tuition deferral");
        assertThat(sql).contains("Consequences of unpaid tuition");
    }

    @Test
    @DisplayName("Topic 4: Academic probation levels (1, 2) and thesis eligibility are covered")
    void v40CoversProbationLevelsAndThesisEligibility() {
        // Vietnamese content assertions
        assertThat(sql).contains("Cảnh báo học vụ các mức và điều kiện bảo vệ khóa luận tốt nghiệp");
        assertThat(sql).contains("Cảnh báo mức 1");
        assertThat(sql).contains("Cảnh báo mức 2");
        assertThat(sql).contains("Buộc thôi học");
        assertThat(sql).contains("Điều kiện thực hiện và bảo vệ đồ án/khóa luận tốt nghiệp");
        assertThat(sql).contains("KHÔNG bị cảnh báo học vụ mức 2 hoặc mức 3");
        assertThat(sql).contains("tối thiểu 75% đến 80% tổng số tín chỉ");

        // English content assertions
        assertThat(sql).contains("Academic probation levels and graduation thesis eligibility");
        assertThat(sql).contains("Level 1 warning");
        assertThat(sql).contains("Level 2 warning");
        assertThat(sql).contains("Academic expulsion");
        assertThat(sql).contains("Graduation thesis eligibility");
    }

    @Test
    @DisplayName("Topic 5: Course withdrawal and semester credit limits are covered")
    void v40CoversWithdrawalAndCreditLimits() {
        // Vietnamese content assertions
        assertThat(sql).contains("Quy định rút học phần và giới hạn tín chỉ mỗi học kỳ");
        assertThat(sql).contains("trong vòng 2 tuần đầu");
        assertThat(sql).contains("điểm chữ W (Withdrawn)");
        assertThat(sql).contains("tối thiểu 14 tín chỉ");
        assertThat(sql).contains("tối đa 24 tín chỉ");
        assertThat(sql).contains("tối đa 28 tín chỉ");
        assertThat(sql).contains("8 đến 10 tín chỉ");

        // English content assertions
        assertThat(sql).contains("Course withdrawal and semester credit limits");
        assertThat(sql).contains("grade W");
        assertThat(sql).contains("minimum of 14 credits");
        assertThat(sql).contains("maximum of 24 credits");
    }

    @Test
    @DisplayName("Topic 6: Merit scholarships and graduation exit requirements are covered")
    void v40CoversScholarshipsAndGraduationRequirements() {
        // Vietnamese content assertions
        assertThat(sql).contains("Tiêu chuẩn học bổng khuyến khích học tập và chuẩn đầu ra tốt nghiệp");
        assertThat(sql).contains("Học bổng khuyến khích học tập (KKHT)");
        assertThat(sql).contains("Học bổng loại Khá");
        assertThat(sql).contains("Học bổng loại Giỏi");
        assertThat(sql).contains("Học bổng loại Xuất sắc");
        assertThat(sql).contains("Điều kiện tốt nghiệp");
        assertThat(sql).contains("Chuẩn đầu ra Ngoại ngữ");
        assertThat(sql).contains("TOEIC từ 450-550");
        assertThat(sql).contains("Chuẩn Tin học (MOS/IC3)");
        assertThat(sql).contains("Giáo dục Quốc phòng - An ninh và Giáo dục Thể chất");

        // English content assertions
        assertThat(sql).contains("Scholarship standards and graduation exit requirements");
        assertThat(sql).contains("Academic merit scholarships");
        assertThat(sql).contains("Graduation requirements");
    }

    @Test
    @DisplayName("V40 activates release 00000000-0000-0000-0000-000000000040 and updates runtime state")
    void v40ConfiguresReleaseAndRuntimeState() {
        String expectedReleaseId = "00000000-0000-0000-0000-000000000040";

        assertThat(sql).contains("'" + expectedReleaseId + "'::uuid");
        assertThat(sql).contains("'local-demo-v40'");
        assertThat(sql).contains("'PUBLISHED'");
        assertThat(sql).contains("assistant.knowledge_runtime_document");
        assertThat(sql).contains("assistant.knowledge_runtime_state (singleton, active_release_id)");
        assertThat(sql).contains("ON CONFLICT (singleton) DO UPDATE");
        assertThat(sql).contains("SET active_release_id = EXCLUDED.active_release_id");
    }

    @Test
    @DisplayName("V40 statements are idempotent with WHERE NOT EXISTS guards")
    void v40IsIdempotent() {
        assertThat(sql).contains("WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document existing WHERE existing.slug = seed.slug)");
        assertThat(sql).contains("WHERE d.source = 'campuscore-academic-regulations'");
        assertThat(sql).contains("AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_document_revision r WHERE r.document_id = d.id AND r.version = 1)");
        assertThat(sql).contains("WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000040'::uuid)");
        assertThat(sql).contains("WHERE p.release_id = '00000000-0000-0000-0000-000000000040'::uuid AND p.source_id = d.id::text");
    }

    @Test
    @DisplayName("V40 script has balanced parentheses and valid SQL terminator statements")
    void v40HasBalancedSyntax() {
        int openParen = 0;
        int closeParen = 0;
        boolean inSingleQuote = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                // Check for escaped single quote ''
                if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    i++; // skip escaped quote
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
