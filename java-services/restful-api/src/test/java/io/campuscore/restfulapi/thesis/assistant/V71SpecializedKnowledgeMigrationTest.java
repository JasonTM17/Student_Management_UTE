package io.campuscore.restfulapi.thesis.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Migration validation test for Flyway V71:
 * V71__specialized_domain_knowledge.sql
 *
 * Verifies that the specialized (Trợ lý chuyên sâu) corpus migration is
 * well-formed, seeds all 12 bilingual documents with domain = 'SPECIALIZED',
 * publishes them through the governed revision + release + runtime projection
 * pipeline, and switches the runtime state pointer to the new release.
 */
class V71SpecializedKnowledgeMigrationTest {

    private static final Path MIGRATION_PATH =
            Path.of("src/main/resources/db/migration/V71__specialized_domain_knowledge.sql");
    private static String sql;

    @BeforeAll
    static void loadMigrationSql() throws Exception {
        assertThat(MIGRATION_PATH)
                .as("Flyway migration V71 file must exist at expected path")
                .exists();
        sql = Files.readString(MIGRATION_PATH);
        assertThat(sql).isNotBlank();
    }

    @Test
    @DisplayName("V71 script follows naming convention and targets the assistant schema")
    void v71FollowsNamingAndSchemaConventions() {
        assertThat(MIGRATION_PATH.getFileName().toString())
                .isEqualTo("V71__specialized_domain_knowledge.sql")
                .startsWith("V71__");

        assertThat(sql)
                .contains("INSERT INTO assistant.knowledge_document")
                .contains("INSERT INTO assistant.knowledge_document_revision")
                .contains("INSERT INTO assistant.knowledge_release")
                .contains("INSERT INTO assistant.knowledge_runtime_document")
                .contains("UPDATE assistant.knowledge_runtime_state");
    }

    @Test
    @DisplayName("V71 seeds all 12 specialized document slugs in both locales")
    void v71SeedsAllSpecializedSlugs() {
        String[] slugBases = {
                "specialized-oop-solid-design-patterns",
                "specialized-relational-database-design-sql-optimization",
                "specialized-software-testing-pyramid-tdd",
                "specialized-git-branching-collaboration-workflow",
                "specialized-rest-api-design-conventions",
                "specialized-microservices-monolith-spring-architecture",
                "specialized-react-nextjs-frontend-patterns",
                "specialized-devops-cicd-docker-kubernetes",
                "specialized-owasp-web-security-fundamentals",
                "specialized-ai-rag-llm-foundations",
                "specialized-clean-code-code-review-standards",
                "specialized-se-career-roadmap-interview-prep",
        };
        for (String base : slugBases) {
            assertThat(sql).contains("'" + base + "-vi'");
            assertThat(sql).contains("'" + base + "-en'");
        }
    }

    @Test
    @DisplayName("V71 extends the governed domain whitelist on all three knowledge tables")
    void v71ExtendsDomainWhitelist() {
        assertThat(sql).contains("assistant_knowledge_domain_valid");
        assertThat(sql).contains("assistant_revision_domain_valid");
        assertThat(sql).contains("assistant_runtime_domain_valid");
        // Each recreated constraint must still carry every pre-existing domain
        // plus SPECIALIZED — a narrowed whitelist would reject the live corpus.
        assertThat(sql)
                .contains("'THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ', 'SPECIALIZED'");
    }

    @Test
    @DisplayName("V71 assigns the SPECIALIZED domain and publishes a v71 release snapshot")
    void v71AssignsSpecializedDomainAndRebuildsRelease() {
        assertThat(sql).contains("'campuscore-specialized-corpus'");
        assertThat(sql).contains("'SPECIALIZED'");
        // Every seeded row carries the SPECIALIZED domain: 24 value tuples.
        Pattern rowDomain = Pattern.compile("\\d+, 'SPECIALIZED'\\)");
        int matches = 0;
        var matcher = rowDomain.matcher(sql);
        while (matcher.find()) {
            matches += 1;
        }
        assertThat(matches)
                .as("12 bilingual documents must each be seeded with domain SPECIALIZED")
                .isEqualTo(24);
        assertThat(sql).contains("00000000-0000-0000-0000-000000000071");
        assertThat(sql).contains("'specialized-domain-corpus-v71'");
        assertThat(sql).contains("state = 'PUBLISHED'");
        assertThat(sql).contains("visibility = 'PUBLIC'");
    }

    @Test
    @DisplayName("V71 mirrors the Supabase authoring corpus byte-for-byte on slugs")
    void v71MatchesSupabaseAuthoringCorpus() throws Exception {
        Path corpus = Path.of("../../supabase/seed/assistant-specialized-knowledge.json");
        assertThat(corpus)
                .as("the Supabase authoring corpus must exist beside the repo root")
                .exists();
        String corpusJson = Files.readString(corpus);
        String[] slugBases = {
                "specialized-oop-solid-design-patterns",
                "specialized-relational-database-design-sql-optimization",
                "specialized-software-testing-pyramid-tdd",
                "specialized-git-branching-collaboration-workflow",
                "specialized-rest-api-design-conventions",
                "specialized-microservices-monolith-spring-architecture",
                "specialized-react-nextjs-frontend-patterns",
                "specialized-devops-cicd-docker-kubernetes",
                "specialized-owasp-web-security-fundamentals",
                "specialized-ai-rag-llm-foundations",
                "specialized-clean-code-code-review-standards",
                "specialized-se-career-roadmap-interview-prep",
        };
        for (String base : slugBases) {
            assertThat(corpusJson).contains("\"" + base + "-vi\"");
            assertThat(corpusJson).contains("\"" + base + "-en\"");
        }
    }

    @Test
    void specializedSupabaseSeedPassesThePublicKnowledgeGuard() throws Exception {
        Path corpus = Path.of("../../supabase/seed/assistant-specialized-knowledge.json");
        var documents = new ObjectMapper().readTree(Files.readString(corpus));
        assertThat(documents.size()).isEqualTo(24);
        var rejected = new java.util.ArrayList<String>();
        for (var document : documents) {
            for (String field : new String[] {"slug", "title", "content", "source"}) {
                var result = AssistantInputGuard.inspectPublicKnowledge(document.path(field).asText());
                if (!result.allowed()) rejected.add(document.path("slug").asText() + ":" + field + ":" + result.reasonCode());
            }
        }
        assertThat(rejected).isEmpty();
    }
}
