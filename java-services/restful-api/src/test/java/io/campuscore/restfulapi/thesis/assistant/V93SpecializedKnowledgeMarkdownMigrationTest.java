package io.campuscore.restfulapi.thesis.assistant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the V93 markdown reformat of the 24 SPECIALIZED domain documents.
 * Degraded assistant answers (QUOTA_EXCEEDED and friends) serve the top
 * document verbatim, so the corpus itself must carry the structure the panel
 * renders: a '## topic' heading and '- ' bullets — never again the inline
 * "1. … N. …" wall of text (round-4 user report). Also guards the V78 guard
 * repair (the blocked phrase must not be resurrected by a rebuild) and the
 * V78-style release projection protocol.
 */
class V93SpecializedKnowledgeMarkdownMigrationTest {

    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V93__specialized_domain_knowledge_markdown.sql");
    private static String sql;

    @BeforeAll
    static void loadMigrationSql() throws Exception {
        assertThat(MIGRATION_PATH)
                .as("Flyway migration V93 file must exist at expected path")
                .exists();
        sql = Files.readString(MIGRATION_PATH);
        assertThat(sql).isNotBlank();
    }

    @Test
    @DisplayName("V93 rewrites all 24 specialized documents")
    void v93RewritesAllTwentyFourDocuments() {
        List<String> topics = List.of(
                "oop-solid-design-patterns", "relational-database-design-sql-optimization",
                "software-testing-pyramid-tdd", "git-branching-collaboration-workflow",
                "rest-api-design-conventions", "microservices-monolith-spring-architecture",
                "react-nextjs-frontend-patterns", "devops-cicd-docker-kubernetes",
                "owasp-web-security-fundamentals", "ai-rag-llm-foundations",
                "clean-code-code-review-standards", "se-career-roadmap-interview-prep");
        for (String topic : topics) {
            assertThat(occurrences(sql, "WHERE slug = 'specialized-" + topic + "-"))
                    .as("one guarded update per locale of %s", topic)
                    .isEqualTo(2);
        }
    }

    @Test
    @DisplayName("V93 follows the release projection protocol")
    void v93FollowsReleaseProjectionProtocol() {
        assertThat(sql)
                .contains("UPDATE assistant.knowledge_document_revision")
                .contains("INSERT INTO assistant.knowledge_document_revision")
                .contains("INSERT INTO assistant.knowledge_release")
                .contains("INSERT INTO assistant.knowledge_runtime_document")
                .contains("UPDATE assistant.knowledge_runtime_state")
                .contains("'00000000-0000-0000-0000-000000000093'::uuid")
                .contains("'specialized-domain-knowledge-markdown-v93'");
    }

    @Test
    @DisplayName("DevOps and relational-DB documents carry markdown structure")
    void devopsAndRelationalDocumentsCarryMarkdown() {
        for (String locale : List.of("vi", "en")) {
            assertThat(markdownBlock("specialized-devops-cicd-docker-kubernetes-" + locale))
                    .contains("## DevOps")
                    .contains("- **");
            assertThat(markdownBlock("specialized-relational-database-design-sql-optimization-" + locale))
                    .contains("## ")
                    .contains("- **");
        }
    }

    @Test
    @DisplayName("The V78 guard repair survives the reformat")
    void v78GuardRepairSurvives() {
        assertThat(sql).contains("yêu cầu thay đổi chỉ dẫn");
        assertThat(sql).doesNotContain("bỏ qua hướng dẫn");
    }

    @Test
    @DisplayName("The inline numbered-paragraph shape is gone")
    void inlineNumberedParagraphShapeIsGone() {
        assertThat(sql).doesNotContain("Kiến thức chuyên môn — Cơ sở dữ liệu quan hệ: 1.");
        assertThat(sql).doesNotContain("Software engineering domain knowledge — ");
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        int at = 0;
        while ((at = haystack.indexOf(needle, at)) >= 0) {
            count += 1;
            at += needle.length();
        }
        return count;
    }

    private static String markdownBlock(String slug) {
        int at = sql.indexOf("WHERE slug = '" + slug + "'");
        assertThat(at).as("slug %s present", slug).isGreaterThan(0);
        // The guarded UPDATE precedes the WHERE clause; take its statement window.
        int start = sql.lastIndexOf("WITH corrected AS (", at);
        int end = sql.indexOf("INSERT INTO v93_changed_document", at);
        assertThat(start).isGreaterThan(0);
        assertThat(end).isGreaterThan(start);
        return sql.substring(start, end);
    }
}
