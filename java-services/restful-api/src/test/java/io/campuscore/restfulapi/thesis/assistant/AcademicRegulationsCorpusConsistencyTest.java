package io.campuscore.restfulapi.thesis.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * never executes V57 or V59. This test therefore asserts on the MIGRATION TEXT
 * itself: it reads the SQL the production database will actually apply and
 * proves that the effective corpus (the last revision of each document across
 * V57 and V59) contains no self-contradictory threshold pair and no undefined
 * warning level, and that every document in the applied V40/V41/V43 corpus that
 * carries a defect is superseded.
 *
 * <p>Wukong falsified the first version of this corpus fix: it reconciled the
 * scholarship tiers to a LINEAR scale conversion, while the application converts
 * through letter bands and V41's curriculum document already stated those bands.
 * Release 0057 therefore shipped two documents giving two different 4.0 values
 * for the same 7.0/10 input, and the linear one disagreed with the transcript the
 * student is looking at. The regression this test now enforces is the one the
 * earlier version was blind to: a threshold pair whose halves contradict each
 * other, and a named letter grade that disagrees with the application's table.
 */
class AcademicRegulationsCorpusConsistencyTest {

    private static final Path V40 = Path.of("src/main/resources/db/migration/V40__seed_academic_regulations_knowledge.sql");
    private static final Path V41 = Path.of("src/main/resources/db/migration/V41__enrich_comprehensive_academic_knowledge.sql");
    private static final Path V43 = Path.of("src/main/resources/db/migration/V43__big_data_campus_ecosystem_enrichment.sql");
    private static final Path V57 = Path.of("src/main/resources/db/migration/V57__reconcile_academic_regulations_knowledge.sql");
    private static final Path V59 = Path.of("src/main/resources/db/migration/V59__align_scholarship_gpa_thresholds_with_band_conversion.sql");

    /**
     * The application's own course-level conversion, read from the frontend table so
     * this test cannot drift from what a student actually sees. V41's curriculum
     * document states the same bands — which is exactly why a corpus converting
     * linearly contradicted both it and the transcript.
     *
     * <p>Located by walking up from the module directory, matching how the sanitizer
     * test finds its shared fixture, so an aggregate build from any directory still
     * resolves the single source of truth.
     */
    private static Path gradeScale() {
        for (Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
                dir != null;
                dir = dir.getParent()) {
            Path candidate = dir.resolve(Paths.get("frontend", "src", "lib", "grade-scale.ts"));
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "frontend/src/lib/grade-scale.ts not found above " + System.getProperty("user.dir"));
    }

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

    /**
     * A "/4.0 (y/10)" threshold pair. After V59 no document may state one: with a
     * banded mapping a 4.0 threshold corresponds to a range of 10-point scores, so
     * stating both halves invites a contradiction with whichever half the
     * application actually computes.
     */
    private static final Pattern GPA_PAIR = Pattern.compile(
            "(\\d(?:\\.\\d+)?)/4\\.0[^\\d]{0,4}(\\d(?:\\.\\d+)?)/10");

    /**
     * A 10-point threshold that names a letter grade, in either locale, so the named
     * grade can be checked against the application's own band table.
     *
     * <p>The trailing lookahead is a negative one rather than {@code \b}: {@code +} and
     * a closing bracket are both non-word characters, so {@code \b} after {@code A\+}
     * never matches and the engine silently falls back to the bare {@code A}
     * alternative, reading "A+" as "A" and "B+" as "B". That made an earlier version
     * of this test report mismatches that did not exist.
     */
    private static final Pattern SCORE_WITH_LETTER = Pattern.compile(
            "(\\d(?:\\.\\d+)?)/10[^.]{0,60}?(?:điểm|letter grade|grade)\\s*"
                    + "(A\\+|A|B\\+|B|C\\+|C|D\\+|D|F)(?![A-Za-z0-9])");

    /** One `min: <x>, max: <y>, point: <p>` band from the frontend conversion table. */
    private static final Pattern BAND = Pattern.compile(
            "letter:\\s*'([A-F][+-]?)',\\s*min:\\s*([\\d.]+),\\s*max:\\s*[\\d.]+,\\s*point:\\s*([\\d.]+)");

    private static String v40;
    private static String v41;
    private static String v43;
    private static String v57;
    private static String v59;
    /** The application's bands: score lower bound -> letter -> 4.0 point. */
    private static final Map<Double, String> BAND_LETTER = new LinkedHashMap<>();
    private static final Map<String, Double> BAND_POINT = new LinkedHashMap<>();
    private static final Map<String, String> revisedContentBySlug = new LinkedHashMap<>();

    @BeforeAll
    static void loadMigrationSql() throws Exception {
        assertThat(V57).as("additive revision migration V57 must exist").exists();
        assertThat(V59)
                .as("V59 must exist: it supersedes V57's linear scholarship conversion, "
                        + "which contradicts the application's banded conversion")
                .exists();
        v40 = Files.readString(V40);
        v41 = Files.readString(V41);
        v43 = Files.readString(V43);
        v57 = Files.readString(V57);
        v59 = Files.readString(V59);

        // Effective corpus = the LAST revision of each document. Applying V59 after
        // V57 is what makes the scholarship documents reflect the band conversion.
        for (String migration : List.of(v57, v59)) {
            Matcher revisions = REVISION_BLOCK.matcher(migration);
            while (revisions.find()) {
                revisedContentBySlug.put(revisions.group(2), revisions.group(1));
            }
        }

        Matcher bands = BAND.matcher(Files.readString(gradeScale()));
        while (bands.find()) {
            BAND_LETTER.put(Double.parseDouble(bands.group(2)), bands.group(1));
            BAND_POINT.put(bands.group(1), Double.parseDouble(bands.group(3)));
        }
        assertThat(BAND_LETTER)
                .as("the band table must be readable from the frontend conversion table")
                .isNotEmpty();
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
    @DisplayName("No effective document states a /4.0 threshold paired with a /10 threshold")
    void noEffectiveDocumentStatesAnAmbiguousConversionPair() {
        assertThat(revisedContentBySlug).isNotEmpty();
        for (Map.Entry<String, String> revision : revisedContentBySlug.entrySet()) {
            Matcher pair = GPA_PAIR.matcher(revision.getValue());
            // Materialize the match before building the message: the group accessors
            // throw when there is no match, so evaluating them eagerly inside as()
            // turns a clean assertion failure into an IllegalStateException.
            boolean found = pair.find();
            String evidence = found ? pair.group(1) + "/4.0 and " + pair.group(2) + "/10" : "";
            assertThat(found)
                    .as("%s states %s. With a banded conversion those two halves cannot both be "
                                    + "authoritative: state the threshold on the 10-point scale and "
                                    + "let the curriculum document own the conversion table.",
                            revision.getKey(), evidence)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("Every named letter grade beside a /10 threshold matches the application's band table")
    void namedLetterGradesAgreeWithTheApplication() {
        assertThat(revisedContentBySlug).isNotEmpty();
        int checked = 0;
        for (Map.Entry<String, String> revision : revisedContentBySlug.entrySet()) {
            Matcher named = SCORE_WITH_LETTER.matcher(revision.getValue());
            while (named.find()) {
                double score = Double.parseDouble(named.group(1));
                String letter = named.group(2);
                String expected = bandLetterFor(score);
                assertThat(letter)
                        .as("%s says %s/10 is grade %s, but the application's table assigns %s "
                                        + "(a student reading this would be told something their "
                                        + "transcript contradicts)",
                                revision.getKey(), score, letter, expected)
                        .isEqualTo(expected);
                checked++;
            }
        }
        assertThat(checked)
                .as("the corpus must actually name letter grades, otherwise this test proves nothing")
                .isGreaterThan(0);
    }

    /**
     * The letter the application assigns to a 10-point score, derived from the
     * frontend table rather than restated here. Flat 0.5 steps make a lookup on the
     * band lower bound exact, which is all the corpus thresholds need (7.0, 8.0, 9.0).
     */
    private static String bandLetterFor(double score) {
        return BAND_LETTER.entrySet().stream()
                .filter(entry -> score >= entry.getKey() && score < entry.getKey() + 0.5)
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "score " + score + " falls on no band in the frontend conversion table"));
    }

    @Test
    @DisplayName("CB-P1-1: the Khá scholarship tier is stated on the 10-point scale with the app's grade")
    void khoTierIsConsistentlyStated() {
        // V57 reconciled this tier to a linear 2.8/4.0 pair, which the application's
        // banded conversion makes wrong (7.0/10 is a B, worth 3.0). V59 states the
        // threshold on the 10-point scale instead of picking a side.
        assertThat(revisedContentBySlug.get("scholarships-graduation-requirements-vi"))
                .contains("Khá: điểm trung bình học kỳ (GPA) từ 7.0/10")
                .contains("tương đương điểm B")
                .doesNotContain("2.5/4.0")
                .doesNotContain("2.8/4.0");
        assertThat(revisedContentBySlug.get("scholarships-graduation-requirements-en"))
                .contains(">= 7.0/10")
                .contains("letter grade B")
                .doesNotContain("2.5/4.0")
                .doesNotContain("2.8/4.0");
        assertThat(revisedContentBySlug.get("academic-scholarship-criteria-vi"))
                .contains("7.0/10")
                .doesNotContain("2.50")
                .doesNotContain("2.80/4.0");
        assertThat(revisedContentBySlug.get("academic-scholarship-criteria-en"))
                .contains("7.0/10")
                .doesNotContain("2.50")
                .doesNotContain("2.80/4.0");
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
}
