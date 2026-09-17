package io.campuscore.restfulapi.thesis.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Corpus-consistency gate for the seeded academic regulations knowledge.
 *
 * <p>The H2 test tree is RENUMBERED against the production tree and carries no
 * counterpart for new production migrations, so a green {@code mvn verify}
 * never executes V57. This test therefore asserts on the MIGRATION TEXT
 * itself: it reads the SQL the production database will actually apply and
 * proves that the effective corpus (V57's revised documents) contains no
 * self-contradictory threshold pair and no undefined warning level, and that
 * every document in the applied V40/V41/V43 corpus that carries a defect is
 * superseded by V57.
 */
class AcademicRegulationsCorpusConsistencyTest {

    private static final Path V40 = Path.of("src/main/resources/db/migration/V40__seed_academic_regulations_knowledge.sql");
    private static final Path V41 = Path.of("src/main/resources/db/migration/V41__enrich_comprehensive_academic_knowledge.sql");
    private static final Path V43 = Path.of("src/main/resources/db/migration/V43__big_data_campus_ecosystem_enrichment.sql");
    private static final Path V57 = Path.of("src/main/resources/db/migration/V57__reconcile_academic_regulations_knowledge.sql");

    /**
     * Every phrase that must not survive in the EFFECTIVE corpus: the undefined
     * warning level (CB-P1-2), the wrong scholarship conversion (CB-P1-1), the
     * superseded eligibility percentage range (CB-P2-1) and the superseded
     * exit-TOEIC range (CB-P2-2), plus the contradictory V43 scholarship tier.
     */
    private static final Pattern VIOLATION = Pattern.compile(
            "mức 3|mức 2/3|mức 2 hoặc mức 3|Level 3 warning|Level 3 academic|Level 2/3"
                    + "|450-550|450–550|TOEIC 450|TOEIC từ 450"
                    + "|75% đến 80%|75% to 80%|tối thiểu 75%"
                    + "|2\\.5/4\\.0 \\(7\\.0/10\\)|GPA >= 2\\.50");

    private static final Pattern VALUES_ROW = Pattern.compile(
            "\\('([a-z0-9-]+)',\\s*'(?:vi|en)',.*?,\\s*\\d+,\\s*'[A-Z_]+'\\)", Pattern.DOTALL);

    private static final Pattern REVISION_BLOCK = Pattern.compile(
            "SET content = '(.+?)'\\s*\\n\\s*WHERE slug = '([a-z0-9-]+)' AND locale = '(vi|en)'",
            Pattern.DOTALL);

    /** GPA pairs in the revised text: the 10-scale value must equal 4.0-scale * 2.5. */
    private static final Pattern GPA_PAIR = Pattern.compile(
            "(\\d(?:\\.\\d)?)/4\\.0[^\\d]*(\\d(?:\\.\\d)?)/10");

    private static String v40;
    private static String v41;
    private static String v43;
    private static String v57;
    private static final Map<String, String> revisedContentBySlug = new LinkedHashMap<>();

    @BeforeAll
    static void loadMigrationSql() throws Exception {
        assertThat(V57).as("additive revision migration V57 must exist").exists();
        v40 = Files.readString(V40);
        v41 = Files.readString(V41);
        v43 = Files.readString(V43);
        v57 = Files.readString(V57);
        Matcher revisions = REVISION_BLOCK.matcher(v57);
        while (revisions.find()) {
            revisedContentBySlug.put(revisions.group(2), revisions.group(1));
        }
    }

    @Test
    @DisplayName("V57 is release-traceable: versioned revisions + immutable release, not a raw UPDATE")
    void v57UsesRevisionAndReleaseMechanism() {
        assertThat(v57).contains("UPDATE assistant.knowledge_document");
        assertThat(v57).contains("UPDATE assistant.knowledge_document_revision");
        assertThat(v57).contains("INSERT INTO assistant.knowledge_document_revision");
        assertThat(v57).contains("state = 'ARCHIVED'");
        assertThat(v57).contains("state, locale, slug, title, content");
        assertThat(v57).contains("INSERT INTO assistant.knowledge_release");
        assertThat(v57).contains("INSERT INTO assistant.knowledge_runtime_document");
        assertThat(v57).contains("INSERT INTO assistant.knowledge_runtime_state");
        assertThat(v57).contains("'00000000-0000-0000-0000-000000000057'::uuid");
        assertThat(v57).contains("academic-regulations-reconcile-v57");
        // The applied migrations are never edited by V57.
        assertThat(v57).doesNotContain("V40__seed_academic_regulations_knowledge.sql'");
    }

    @Test
    @DisplayName("Revised corpus contains no undefined warning level and no contradictory threshold")
    void revisedCorpusHasNoViolations() {
        assertThat(revisedContentBySlug).as("V57 must revise documents").isNotEmpty();
        for (Map.Entry<String, String> revision : revisedContentBySlug.entrySet()) {
            Matcher violation = VIOLATION.matcher(revision.getValue());
            assertThat(violation.find())
                    .as("revised document %s still carries a violating phrase: %s",
                            revision.getKey(), violation.regionStart() >= 0 ? "matched" : "none")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("Every GPA conversion pair in the revised corpus is self-consistent (x/4.0 = y/10 x 0.4)")
    void revisedGpaConversionPairsAreConsistent() {
        assertThat(revisedContentBySlug).isNotEmpty();
        for (Map.Entry<String, String> revision : revisedContentBySlug.entrySet()) {
            Matcher pair = GPA_PAIR.matcher(revision.getValue());
            while (pair.find()) {
                double onScale4 = Double.parseDouble(pair.group(1));
                double onScale10 = Double.parseDouble(pair.group(2));
                assertThat(onScale10)
                        .as("%s: %s/4.0 must convert to %s/10 (expected %.2f)",
                                revision.getKey(), onScale4, onScale10, onScale4 * 2.5)
                        .isCloseTo(onScale4 * 2.5, within(0.05));
            }
        }
    }

    @Test
    @DisplayName("CB-P1-1: the Khá scholarship tier is 2.8/4.0 (7.0/10) in both locales and V43 criteria")
    void khoTierIsConsistentlyTwoPointEight() {
        assertThat(revisedContentBySlug.get("scholarships-graduation-requirements-vi"))
                .contains("Học bổng loại Khá: GPA từ 2.8/4.0 (7.0/10)");
        assertThat(revisedContentBySlug.get("scholarships-graduation-requirements-en"))
                .contains("Fair scholarship: GPA >= 2.8/4.0 (7.0/10)");
        assertThat(revisedContentBySlug.get("academic-scholarship-criteria-vi"))
                .contains("GPA >= 2.80")
                .doesNotContain("2.50");
        assertThat(revisedContentBySlug.get("academic-scholarship-criteria-en"))
                .contains("GPA >= 2.80")
                .doesNotContain("2.50");
    }

    @Test
    @DisplayName("CB-P1-2: the two-level warning scheme is stated explicitly (no level 3)")
    void warningSchemeIsTwoLevel() {
        assertThat(revisedContentBySlug.get("academic-probation-and-thesis-eligibility-vi"))
                .contains("Cảnh báo mức 1")
                .contains("Cảnh báo mức 2")
                .contains("Bị cảnh báo học vụ mức 2 trong hai học kỳ chính liên tiếp");
        assertThat(revisedContentBySlug.get("academic-probation-and-thesis-eligibility-en"))
                .contains("Level 1 warning")
                .contains("Level 2 warning")
                .contains("Two consecutive Level 2 warnings");
    }

    @Test
    @DisplayName("CB-P2-1: thesis eligibility is one canonical set stated identically in V40 and V43 documents")
    void thesisEligibilityIsCanonical() {
        String vi40 = revisedContentBySlug.get("academic-probation-and-thesis-eligibility-vi");
        String vi43 = revisedContentBySlug.get("graduation-thesis-eligibility-defense-vi");
        String en40 = revisedContentBySlug.get("academic-probation-and-thesis-eligibility-en");
        String en43 = revisedContentBySlug.get("graduation-thesis-eligibility-defense-en");
        assertThat(vi40).isNotNull();
        assertThat(vi43).isNotNull();
        assertThat(en40).isNotNull();
        assertThat(en43).isNotNull();
        for (String token : List.of("tối thiểu 110 tín chỉ", "CPA từ 2.00/4.0 trở lên",
                "không bị cảnh báo học vụ mức 2 đang trong hiệu lực", "không bị kỷ luật học vụ",
                "học phần chuyên ngành cốt lõi", "thực tập chuyên ngành")) {
            assertThat(vi40).as("V40 vi canonical token: %s", token).contains(token);
            assertThat(vi43).as("V43 vi canonical token: %s", token).contains(token);
        }
        for (String token : List.of("at least 110 credits", "at least 2.00/4.0",
                "active Level 2 academic warning", "disciplinary sanction",
                "core specialized courses", "specialized internship")) {
            assertThat(en40).as("V40 en canonical token: %s", token).contains(token);
            assertThat(en43).as("V43 en canonical token: %s", token).contains(token);
        }
    }

    @Test
    @DisplayName("CB-P2-2: exit TOEIC is the canonical 500/600 split, never a range")
    void exitToeiThresholdIsCanonical() {
        assertThat(revisedContentBySlug.get("scholarships-graduation-requirements-vi"))
                .contains("TOEIC từ 500 điểm với khối ngành kỹ thuật và công nghệ")
                .contains("từ 600 điểm với khối ngành kinh tế và chương trình chất lượng cao");
        assertThat(revisedContentBySlug.get("scholarships-graduation-requirements-en"))
                .contains("TOEIC 500 for Engineering and Technology majors")
                .contains("TOEIC 600 for Economics and High-Quality programs");
    }

    @Test
    @DisplayName("Every applied-corpus document carrying a defect is superseded by V57")
    void everyViolatingDocumentIsRevised() {
        Set<String> revisedSlugs = new HashSet<>(revisedContentBySlug.keySet());
        Set<String> violating = violationsAcross(v40, v41, v43);
        assertThat(violating)
                .as("the defect evidence must still exist in the applied V40/V41/V43 files "
                        + "(they are never edited); V57 supersedes them")
                .isNotEmpty();
        assertThat(revisedSlugs)
                .as("documents carrying violations must be revised by V57")
                .containsAll(violating);
    }

    private static Set<String> violationsAcross(String... migrations) {
        Set<String> violating = new HashSet<>();
        for (String migration : migrations) {
            Matcher rows = VALUES_ROW.matcher(migration);
            while (rows.find()) {
                if (VIOLATION.matcher(rows.group()).find()) {
                    violating.add(rows.group(1));
                }
            }
        }
        return violating;
    }

    @Test
    @DisplayName("V57 revision blocks are balanced SQL: every content UPDATE pairs with a slug guard")
    void v57UpdateStatementsAreWellFormed() {
        Matcher updates = Pattern.compile("UPDATE assistant\\.knowledge_document\\s+SET content = ",
                Pattern.DOTALL).matcher(v57);
        int updateCount = 0;
        while (updates.find()) updateCount++;
        Matcher guards = Pattern.compile("WHERE slug = '[a-z0-9-]+' AND locale = '(?:vi|en)'").matcher(v57);
        int guardCount = 0;
        while (guards.find()) guardCount++;
        assertThat(guardCount).isEqualTo(updateCount).isEqualTo(10);
        List<String> expectedSlugs = new ArrayList<>(List.of(
                "academic-probation-and-thesis-eligibility-vi",
                "academic-probation-and-thesis-eligibility-en",
                "scholarships-graduation-requirements-vi",
                "scholarships-graduation-requirements-en",
                "leave-of-absence-and-deferment-vi",
                "leave-of-absence-and-deferment-en",
                "graduation-thesis-eligibility-defense-vi",
                "graduation-thesis-eligibility-defense-en",
                "academic-scholarship-criteria-vi",
                "academic-scholarship-criteria-en"));
        assertThat(revisedContentBySlug.keySet()).containsExactlyInAnyOrderElementsOf(expectedSlugs);
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
