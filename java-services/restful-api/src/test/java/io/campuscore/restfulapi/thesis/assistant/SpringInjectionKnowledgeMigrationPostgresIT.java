package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import io.campuscore.restfulapi.thesis.assistant.properties.AssistantProperties;
import io.campuscore.restfulapi.thesis.assistant.repository.ThesisAssistantKnowledgeRepository;
import io.campuscore.restfulapi.thesis.assistant.service.ThesisAssistantService;

/** New disposable local databases only; no clean/reset/drop or shared target. */
@EnabledIfEnvironmentVariable(named = "CAMPUSCORE_SPRING_FOCUS_PG_ENABLED", matches = "true")
class SpringInjectionKnowledgeMigrationPostgresIT {
    private static final String NEXT = "00000000-0000-0000-0000-000000000115";
    private static final String VI = "specialized-microservices-monolith-spring-architecture-vi";
    private static final String DI = "specialized-spring-dependency-constructor-injection-vi";

    @ParameterizedTest
    @ValueSource(strings = {"clean", "human", "foreign", "runtime_drift", "draft", "collision"})
    void upgradesRespectAuthoringAndRuntimeAuthority(String scenario) throws Exception {
        String adminUrl = System.getenv("CAMPUSCORE_SPRING_FOCUS_PG_ADMIN_URL");
        URI target = URI.create(adminUrl.replaceFirst("^jdbc:", ""));
        assertTrue(List.of("localhost", "127.0.0.1", "::1").contains(target.getHost()));
        assertEquals("/postgres", target.getPath());
        assertNull(target.getUserInfo());
        String user = System.getenv("CAMPUSCORE_SPRING_FOCUS_PG_USER");
        String password = System.getenv("CAMPUSCORE_SPRING_FOCUS_PG_PASSWORD");
        String database = "spring_focus_test_" + scenario + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        assertTrue(database.length() <= 63, "PostgreSQL identifiers must not be truncated");
        try (Connection admin = DriverManager.getConnection(adminUrl, user, password)) {
            execute(admin, "CREATE DATABASE " + database);
        }
        String url = adminUrl.substring(0, adminUrl.lastIndexOf('/') + 1) + database;
        Flyway flyway = flyway(url, user, password, "114");
        flyway.migrate();
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            assertEquals(database, scalar(connection, "SELECT current_database()"));
            assertEquals("3", scalar(connection, "SELECT version::text FROM assistant.knowledge_document_revision WHERE state='PUBLISHED' AND slug='" + VI + "'"));
            assertTrue(scalar(connection, "SELECT content FROM assistant.knowledge_document WHERE slug='" + VI + "'").contains("V1–V71"));
            String oldHistory = fingerprint(connection, "SELECT to_jsonb(r)::text FROM assistant.knowledge_release r UNION ALL SELECT to_jsonb(p)::text FROM assistant.knowledge_runtime_document p");
            String previous = scalar(connection, "SELECT active_release_id::text FROM assistant.knowledge_runtime_state WHERE singleton");
            switch (scenario) {
                case "human" -> execute(connection, "UPDATE assistant.knowledge_document SET content='Human architecture wording' WHERE slug='" + VI + "'");
                case "foreign" -> {
                    execute(connection, "INSERT INTO assistant.knowledge_release (id,corpus_version,corpus_hash,row_count,source,status,manifest,created_by) VALUES ('11111111-1111-1111-1111-111111111115','foreign-focus',repeat('f',64),0,'SUPABASE','PUBLISHED','{}','external-office')");
                    execute(connection, "UPDATE assistant.knowledge_runtime_state SET active_release_id='11111111-1111-1111-1111-111111111115' WHERE singleton");
                }
                case "runtime_drift" -> execute(connection, "UPDATE assistant.knowledge_runtime_document SET content='Newer governed runtime' WHERE release_id='" + previous + "' AND slug='" + VI + "'");
                case "draft" -> execute(connection, "INSERT INTO assistant.knowledge_document_revision (id,document_id,version,state,domain,locale,slug,title,content,source,priority,created_by) SELECT '22222222-2222-2222-2222-222222222115',id,4,'DRAFT',domain,locale,slug,title,'Human draft',source,priority,'human' FROM assistant.knowledge_document WHERE slug='" + VI + "'");
                case "collision" -> execute(connection, "INSERT INTO assistant.knowledge_document(id,slug,locale,title,content,source,priority,domain) VALUES ('33333333-3333-3333-3333-333333333115','" + DI + "','vi','Human DI topic','Human DI content','human',10,'SPECIALIZED')");
                default -> { }
            }
            String authorityBefore = scalar(connection, "SELECT active_release_id::text FROM assistant.knowledge_runtime_state WHERE singleton");
            String runtimeBefore = fingerprint(connection, "SELECT to_jsonb(p)::text FROM assistant.knowledge_runtime_document p WHERE release_id='" + authorityBefore + "'");
            String unrelatedBefore = fingerprint(connection, "SELECT to_jsonb(d)::text FROM assistant.knowledge_document d WHERE slug NOT LIKE 'specialized-microservices-monolith-spring-architecture-%' AND slug NOT LIKE 'specialized-spring-dependency-constructor-injection-%'");
            String guardedBefore = fingerprint(connection, "SELECT to_jsonb(d)::text FROM assistant.knowledge_document d WHERE slug='" + (scenario.equals("collision") ? DI : VI) + "' UNION ALL SELECT to_jsonb(r)::text FROM assistant.knowledge_document_revision r WHERE slug='" + (scenario.equals("collision") ? DI : VI) + "'");
            flyway(url, user, password, "115").migrate();
            assertTrue(flyway(url, user, password, "115").validateWithResult().validationSuccessful);
            assertEquals(unrelatedBefore, fingerprint(connection, "SELECT to_jsonb(d)::text FROM assistant.knowledge_document d WHERE slug NOT LIKE 'specialized-microservices-monolith-spring-architecture-%' AND slug NOT LIKE 'specialized-spring-dependency-constructor-injection-%'"));
            if (scenario.equals("clean")) {
                assertEquals(NEXT, scalar(connection, "SELECT active_release_id::text FROM assistant.knowledge_runtime_state WHERE singleton"));
                assertEquals(oldHistory, fingerprint(connection, "SELECT to_jsonb(r)::text FROM assistant.knowledge_release r WHERE id<>'" + NEXT + "' UNION ALL SELECT to_jsonb(p)::text FROM assistant.knowledge_runtime_document p WHERE release_id<>'" + NEXT + "'"));
                for (String locale : List.of("vi", "en")) {
                    String content = scalar(connection, "SELECT content FROM assistant.knowledge_runtime_document WHERE release_id='" + NEXT + "' AND slug='specialized-microservices-monolith-spring-architecture-" + locale + "'");
                    assertFalse(content.contains("35 controller"));
                    assertFalse(content.contains("V1–V71"));
                }
                var repository = new ThesisAssistantKnowledgeRepository(new NamedParameterJdbcTemplate(new DriverManagerDataSource(url, user, password)), false, true, 1000);
                var assistant = new ThesisAssistantService(repository, null, null, null, null, null, null, new AssistantProperties(6000, 2000, 20, 200, 90, true));
                for (String locale : List.of("vi", "en")) {
                    String question = locale.equals("vi") ? "Giải thích ngắn gọn Dependency Injection trong Spring Boot và cho một ví dụ constructor injection." : "Explain Dependency Injection in Spring Boot with a constructor injection example.";
                    var response = assistant.lexicalFastPath(question, locale, "specialized");
                    assertNotNull(response, "real PG ranking must find the focused article");
                    assertEquals("specialized-spring-dependency-constructor-injection-" + locale, response.citations().getFirst().slug());
                    assertTrue(response.answer().contains("public OrderService(PaymentGateway gateway)"));
                }
                String newProjection = fingerprint(connection, "SELECT to_jsonb(p)::text FROM assistant.knowledge_runtime_document p WHERE release_id='" + NEXT + "'");
                flyway(url, user, password, "115").migrate();
                assertEquals(newProjection, fingerprint(connection, "SELECT to_jsonb(p)::text FROM assistant.knowledge_runtime_document p WHERE release_id='" + NEXT + "'"));
            } else {
                assertEquals(authorityBefore, scalar(connection, "SELECT active_release_id::text FROM assistant.knowledge_runtime_state WHERE singleton"));
                assertEquals(runtimeBefore, fingerprint(connection, "SELECT to_jsonb(p)::text FROM assistant.knowledge_runtime_document p WHERE release_id='" + authorityBefore + "'"));
                if (List.of("human", "draft", "collision").contains(scenario)) {
                    assertEquals(guardedBefore, fingerprint(connection, "SELECT to_jsonb(d)::text FROM assistant.knowledge_document d WHERE slug='" + (scenario.equals("collision") ? DI : VI) + "' UNION ALL SELECT to_jsonb(r)::text FROM assistant.knowledge_document_revision r WHERE slug='" + (scenario.equals("collision") ? DI : VI) + "'"));
                }
                assertEquals("AUTHORITY_REVIEW_REQUIRED", scalar(connection, "SELECT \"afterState\"::jsonb->>'runtimeStatus' FROM campuscore_audit.\"AdminAudit\" WHERE \"id\"='audit-specialized-spring-focus-v115'"));
            }
        }
    }

    private static Flyway flyway(String url, String user, String password, String version) {
        return Flyway.configure().dataSource(url, user, password).schemas("thesis").defaultSchema("thesis")
                .locations("classpath:db/migration").target(version).cleanDisabled(true).load();
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private static String scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    private static String fingerprint(Connection connection, String sql) throws Exception {
        return scalar(connection, "SELECT md5(COALESCE(string_agg(value,E'\\n' ORDER BY value),'')) FROM (" + sql + ") data(value)");
    }
}
