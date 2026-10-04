package io.campuscore.restfulapi.thesis.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Opt-in, forward-only PostgreSQL proof. The caller supplies four different disposable databases
 * named policy_migration_test_*. No schema/database is cleaned, reset or dropped by this test.
 * Policy assertions concern the repository's canonical/demo knowledge, not current institution law.
 */
@EnabledIfEnvironmentVariable(named = "CAMPUSCORE_POLICY_MIGRATION_ENABLED", matches = "true")
class StudentPolicyCorrectionMigrationPostgresIT {
    private static final UUID V95_RELEASE = UUID.fromString("00000000-0000-0000-0000-000000000095");
    private static final UUID V96_RELEASE = UUID.fromString("00000000-0000-0000-0000-000000000096");
    private static final String SEED_SOURCE = "campuscore-student-policy-completion";
    private static final int V95_CHECKSUM = 359601249;

    @Test
    void localV95UpgradesToSourceAlignedV96WithoutChangingTheHistoricalRelease() throws Exception {
        Fixture fixture = fixture("LOCAL");
        fixture.migrate("95");
        try (Connection connection = fixture.connect()) {
            assertV95(connection);
            List<UUID> seeds = originalSeedIds(connection);
            String historicalRelease = releaseFingerprint(connection, V95_RELEASE);
            String historicalRuntime = runtimeFingerprint(connection, V95_RELEASE);
            String nonSeedAuthoring = fingerprint(connection, """
                    SELECT to_jsonb(d) AS document,
                           (SELECT jsonb_agg(to_jsonb(r) ORDER BY r.version)
                              FROM assistant.knowledge_document_revision r WHERE r.document_id=d.id) AS revisions
                      FROM assistant.knowledge_document d WHERE NOT (d.id=ANY(?::uuid[]))
                    """, seeds.toArray(UUID[]::new));
            assertThat(runtimeContent(connection, V95_RELEASE, "catalog-attendance-policy-vi"))
                    .contains("vắng quá 80%");
            String v1Payloads = fingerprint(connection, """
                    SELECT document_id,version,locale,slug,title,content,source,priority,created_by,reviewed_by,domain
                      FROM assistant.knowledge_document_revision WHERE document_id=ANY(?::uuid[]) AND version=1
                    """, seeds.toArray(UUID[]::new));

            fixture.migrate("96");
            assertForwardHistory(connection, fixture);
            assertHistoricalRelease(connection, historicalRelease, historicalRuntime);
            assertThat(activeRelease(connection)).isEqualTo(V96_RELEASE.toString());
            assertThat(scalar(connection, "SELECT corpus_version FROM assistant.knowledge_release WHERE id=?", V96_RELEASE))
                    .isEqualTo("local-demo-v96");
            assertThat(scalar(connection, "SELECT previous_release_id::text FROM assistant.knowledge_release WHERE id=?", V96_RELEASE))
                    .isEqualTo(V95_RELEASE.toString());
            assertThat(count(connection, """
                    SELECT COUNT(*) FROM assistant.knowledge_document_revision
                     WHERE document_id=ANY(?::uuid[]) AND version=2 AND state='PUBLISHED'
                    """, seeds.toArray(UUID[]::new))).isEqualTo(24);
            assertThat(fingerprint(connection, """
                    SELECT document_id,version,locale,slug,title,content,source,priority,created_by,reviewed_by,domain
                      FROM assistant.knowledge_document_revision WHERE document_id=ANY(?::uuid[]) AND version=1
                    """, seeds.toArray(UUID[]::new))).isEqualTo(v1Payloads);
            assertThat(fingerprint(connection, """
                    SELECT to_jsonb(d) AS document,
                           (SELECT jsonb_agg(to_jsonb(r) ORDER BY r.version)
                              FROM assistant.knowledge_document_revision r WHERE r.document_id=d.id) AS revisions
                      FROM assistant.knowledge_document d WHERE NOT (d.id=ANY(?::uuid[]))
                    """, seeds.toArray(UUID[]::new))).isEqualTo(nonSeedAuthoring);
            assertSourceAlignedPolicyCopy(connection);
            assertLatestPublishedProjection(connection, V96_RELEASE);
            String stableRelease = releaseFingerprint(connection, V96_RELEASE);
            String stableRuntime = runtimeFingerprint(connection, V96_RELEASE);
            fixture.migrate("96");
            assertThat(releaseFingerprint(connection, V96_RELEASE)).isEqualTo(stableRelease);
            assertThat(runtimeFingerprint(connection, V96_RELEASE)).isEqualTo(stableRuntime);
        }
    }

    @Test
    void v96RestoresThePublishedSupabaseAuthorityThatV95Displaced() throws Exception {
        Fixture fixture = fixture("PREVIOUS_SUPABASE");
        fixture.migrate("94");
        UUID external;
        String externalRelease;
        String externalRuntime;
        try (Connection connection = fixture.connect()) {
            external = publishExternalFixture(connection);
            externalRelease = releaseFingerprint(connection, external);
            externalRuntime = runtimeFingerprint(connection, external);
        }
        fixture.migrate("95");
        try (Connection connection = fixture.connect()) {
            assertV95(connection);
            assertThat(activeRelease(connection)).isEqualTo(V95_RELEASE.toString());
            assertThat(scalar(connection, "SELECT previous_release_id::text FROM assistant.knowledge_release WHERE id=?", V95_RELEASE))
                    .isEqualTo(external.toString());
            String historicalRelease = releaseFingerprint(connection, V95_RELEASE);
            String historicalRuntime = runtimeFingerprint(connection, V95_RELEASE);
            fixture.migrate("96");
            assertForwardHistory(connection, fixture);
            assertThat(activeRelease(connection)).isEqualTo(external.toString());
            assertThat(releaseFingerprint(connection, external)).isEqualTo(externalRelease);
            assertThat(runtimeFingerprint(connection, external)).isEqualTo(externalRuntime);
            assertHistoricalRelease(connection, historicalRelease, historicalRuntime);
        }
    }

    @Test
    void v96DoesNotReplaceAnAlreadyActivePublishedSupabaseRelease() throws Exception {
        Fixture fixture = fixture("CURRENT_SUPABASE");
        fixture.migrate("95");
        try (Connection connection = fixture.connect()) {
            assertV95(connection);
            String historicalRelease = releaseFingerprint(connection, V95_RELEASE);
            String historicalRuntime = runtimeFingerprint(connection, V95_RELEASE);
            UUID external = publishExternalFixture(connection);
            String externalRelease = releaseFingerprint(connection, external);
            String externalRuntime = runtimeFingerprint(connection, external);
            fixture.migrate("96");
            assertForwardHistory(connection, fixture);
            assertThat(activeRelease(connection)).isEqualTo(external.toString());
            assertThat(releaseFingerprint(connection, external)).isEqualTo(externalRelease);
            assertThat(runtimeFingerprint(connection, external)).isEqualTo(externalRuntime);
            assertHistoricalRelease(connection, historicalRelease, historicalRuntime);
        }
    }

    @Test
    void v96PreservesHumanRevisionDocumentAndInFlightEdits() throws Exception {
        Fixture fixture = fixture("HUMAN");
        fixture.migrate("95");
        try (Connection observer = fixture.connect(); Connection human = fixture.connect()) {
            assertV95(observer);
            List<UUID> seeds = originalSeedIds(observer);
            UUID publishedHuman = documentId(observer, "policy-discipline-appeal-en");
            UUID documentHuman = documentId(observer, "faq-student-card-vi");
            UUID revisionHuman = documentId(observer, "faq-notification-center-en");
            UUID inFlightHuman = documentId(observer, "campus-announcements-guide-en");
            execute(observer, "UPDATE assistant.knowledge_document_revision SET state='ARCHIVED' WHERE document_id=? AND state='PUBLISHED'", publishedHuman);
            execute(observer, """
                    INSERT INTO assistant.knowledge_document_revision
                        (id,document_id,version,state,locale,slug,title,content,source,priority,created_by,reviewed_by,published_at,domain)
                    SELECT ?,d.id,2,'PUBLISHED',d.locale,d.slug,'Human published title','Human published policy content',
                           d.source,d.priority,'policy-it-human','policy-it-human',CURRENT_TIMESTAMP,d.domain
                      FROM assistant.knowledge_document d WHERE d.id=?
                    """, UUID.randomUUID(), publishedHuman);
            execute(observer, "UPDATE assistant.knowledge_document SET title='Human document title',content='Human document content' WHERE id=?", documentHuman);
            execute(observer, """
                    UPDATE assistant.knowledge_document_revision
                       SET title='Human revision title',content='Human revision content',created_by='policy-it-human'
                     WHERE document_id=? AND version=1
                    """, revisionHuman);
            String revisionFingerprint = authoringFingerprint(observer, publishedHuman);
            String documentFingerprint = authoringFingerprint(observer, documentHuman);
            String changedV1Fingerprint = authoringFingerprint(observer, revisionHuman);
            String historicalRelease = releaseFingerprint(observer, V95_RELEASE);
            String historicalRuntime = runtimeFingerprint(observer, V95_RELEASE);
            human.setAutoCommit(false);
            execute(human, "UPDATE assistant.knowledge_document SET title='Committed concurrent human title',content='Committed concurrent human content' WHERE id=?", inFlightHuman);
            int humanPid = count(human, "SELECT pg_backend_pid()");
            var executor = Executors.newSingleThreadExecutor();
            try {
                var upgrading = executor.submit(() -> { fixture.migrate("96"); return true; });
                boolean blocked = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (System.nanoTime() < deadline && !upgrading.isDone()) {
                    blocked = count(observer, """
                            SELECT COUNT(*) FROM pg_stat_activity a
                             WHERE a.datname=current_database() AND ?=ANY(pg_blocking_pids(a.pid))
                            """, humanPid) > 0;
                    if (blocked) break;
                    Thread.sleep(25);
                }
                assertThat(blocked).as("V96 waits for the human document writer before selecting hash-guarded corrections").isTrue();
                human.commit();
                assertThat(upgrading.get(30, TimeUnit.SECONDS)).isTrue();
            } finally {
                human.rollback();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            }
            assertForwardHistory(observer, fixture);
            assertHistoricalRelease(observer, historicalRelease, historicalRuntime);
            assertThat(authoringFingerprint(observer, publishedHuman)).isEqualTo(revisionFingerprint);
            assertThat(authoringFingerprint(observer, documentHuman)).isEqualTo(documentFingerprint);
            assertThat(authoringFingerprint(observer, revisionHuman)).isEqualTo(changedV1Fingerprint);
            assertThat(scalar(observer, "SELECT title||'|'||content FROM assistant.knowledge_document WHERE id=?", inFlightHuman))
                    .isEqualTo("Committed concurrent human title|Committed concurrent human content");
            assertThat(count(observer, "SELECT COUNT(*) FROM assistant.knowledge_document_revision WHERE document_id=? AND version>1", inFlightHuman))
                    .isZero();
            UUID[] untouched = seeds.stream().filter(id -> !List.of(publishedHuman, documentHuman, revisionHuman, inFlightHuman).contains(id))
                    .toArray(UUID[]::new);
            assertThat(untouched).hasSize(20);
            assertThat(count(observer, """
                    SELECT COUNT(*) FROM assistant.knowledge_document_revision
                     WHERE document_id=ANY(?::uuid[]) AND version=2 AND state='PUBLISHED'
                    """, untouched)).isEqualTo(20);
            assertThat(activeRelease(observer)).isEqualTo(V96_RELEASE.toString());
            assertLatestPublishedProjection(observer, V96_RELEASE);
        }
    }

    private static void assertSourceAlignedPolicyCopy(Connection connection) throws SQLException {
        String attendanceVi = runtimeContent(connection, V96_RELEASE, "catalog-attendance-policy-vi");
        String attendanceEn = runtimeContent(connection, V96_RELEASE, "catalog-attendance-policy-en");
        assertThat(attendanceVi).contains("20%").doesNotContain("vắng quá 80%");
        assertThat(attendanceEn).contains("20%").doesNotContain("absent from more than 80%");
        assertThat(runtimeContent(connection, V96_RELEASE, "faq-student-certificates-vi"))
                .doesNotContain("gửi yêu cầu ngay trên cổng", "hệ thống ghi nhận trạng thái từ lúc tiếp nhận đến khi hoàn thành");
        assertThat(runtimeContent(connection, V96_RELEASE, "faq-student-certificates-en"))
                .doesNotContain("request them directly in the portal", "system tracks the status from intake to completion");
        assertThat(runtimeContent(connection, V96_RELEASE, "policy-tuition-refund-vi"))
                .doesNotContain("trong hai tuần đầu", "hoàn đầy đủ");
        assertThat(runtimeContent(connection, V96_RELEASE, "policy-tuition-refund-en"))
                .doesNotContain("first two weeks", "usually fully refunded");
        assertThat(runtimeContent(connection, V96_RELEASE, "reg-credit-transfer-vi"))
                .doesNotContain("hai phần ba", "2/3");
        assertThat(runtimeContent(connection, V96_RELEASE, "reg-credit-transfer-en"))
                .doesNotContain("two thirds", "2/3");
    }

    private static void assertLatestPublishedProjection(Connection connection, UUID release) throws SQLException {
        assertThat(count(connection, "SELECT COUNT(*) FROM assistant.knowledge_runtime_document WHERE release_id=?", release))
                .isEqualTo(count(connection, "SELECT row_count FROM assistant.knowledge_release WHERE id=?", release));
        assertThat(count(connection, """
                SELECT COUNT(*) FROM assistant.knowledge_runtime_document p
                 WHERE p.release_id=? AND (p.revision_id IS NULL OR p.version<>(
                    SELECT MAX(r.version) FROM assistant.knowledge_document_revision r
                     WHERE r.document_id::text=p.source_id AND r.state='PUBLISHED'))
                """, release)).isZero();
        assertThat(count(connection, """
                WITH latest AS (
                    SELECT DISTINCT ON (document_id) * FROM assistant.knowledge_document_revision
                     WHERE state='PUBLISHED' ORDER BY document_id,version DESC
                ), expected AS (
                    SELECT d.id::text AS source_id,r.id AS revision_id,r.version,r.domain,r.slug,r.locale,
                           r.title,r.content,r.source,r.priority,TRUE AS active,'PUBLIC'::varchar AS visibility
                      FROM assistant.knowledge_document d JOIN latest r ON r.document_id=d.id
                     WHERE d.active AND d.visibility='PUBLIC'
                ), actual AS (
                    SELECT source_id,revision_id,version,domain,slug,locale,title,content,source,priority,active,visibility
                      FROM assistant.knowledge_runtime_document WHERE release_id=?
                ) SELECT COUNT(*) FROM ((SELECT * FROM expected EXCEPT SELECT * FROM actual)
                    UNION ALL (SELECT * FROM actual EXCEPT SELECT * FROM expected)) differences
                """, release)).isZero();
    }

    private static void assertV95(Connection connection) throws SQLException {
        assertThat(scalar(connection, "SELECT version FROM thesis.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"))
                .isEqualTo("95");
        assertThat(count(connection, "SELECT checksum FROM thesis.flyway_schema_history WHERE version='95' AND success"))
                .isEqualTo(V95_CHECKSUM);
        assertThat(originalSeedIds(connection)).hasSize(24);
    }

    private static void assertForwardHistory(Connection connection, Fixture fixture) throws SQLException {
        assertThat(scalar(connection, "SELECT version FROM thesis.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"))
                .isEqualTo("96");
        assertThat(count(connection, "SELECT checksum FROM thesis.flyway_schema_history WHERE version='95' AND success"))
                .isEqualTo(V95_CHECKSUM);
        assertThat(fixture.flyway("96").validateWithResult().validationSuccessful).isTrue();
    }

    private static void assertHistoricalRelease(Connection connection, String releaseFingerprint, String runtimeFingerprint) throws SQLException {
        assertThat(releaseFingerprint(connection, V95_RELEASE)).isEqualTo(releaseFingerprint);
        assertThat(runtimeFingerprint(connection, V95_RELEASE)).isEqualTo(runtimeFingerprint);
    }

    private static UUID publishExternalFixture(Connection connection) throws SQLException {
        UUID release = UUID.randomUUID();
        execute(connection, """
                INSERT INTO assistant.knowledge_release
                    (id,corpus_version,corpus_hash,row_count,source,status,manifest,created_by,activated_at,previous_release_id)
                VALUES (?,? ,encode(thesis.digest(?,'sha256'),'hex'),1,'SUPABASE','PUBLISHED',
                        jsonb_build_object('schemaVersion',1,'rowCount',1),'policy-it-external',CURRENT_TIMESTAMP,
                        (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton=TRUE))
                """, release, "policy-it-external-" + release, release.toString());
        execute(connection, """
                INSERT INTO assistant.knowledge_runtime_document
                    (release_id,source_id,version,domain,slug,locale,title,content,source,priority,active,visibility)
                VALUES (?,'external-policy-fixture',7,'POLICY','external-policy-fixture','en',
                        'Externally approved policy','Externally approved content','policy-it-external',1,TRUE,'PUBLIC')
                """, release);
        execute(connection, "UPDATE assistant.knowledge_runtime_state SET active_release_id=?,updated_at=CURRENT_TIMESTAMP WHERE singleton=TRUE", release);
        return release;
    }

    private static List<UUID> originalSeedIds(Connection connection) throws SQLException {
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM assistant.knowledge_document WHERE source=? ORDER BY id")) {
            statement.setString(1, SEED_SOURCE);
            try (var rows = statement.executeQuery()) { while (rows.next()) ids.add(rows.getObject(1, UUID.class)); }
        }
        return ids;
    }

    private static UUID documentId(Connection connection, String slug) throws SQLException {
        return UUID.fromString(scalar(connection, "SELECT id::text FROM assistant.knowledge_document WHERE slug=?", slug));
    }

    private static String activeRelease(Connection connection) throws SQLException {
        return scalar(connection, "SELECT active_release_id::text FROM assistant.knowledge_runtime_state WHERE singleton=TRUE");
    }

    private static String runtimeContent(Connection connection, UUID release, String slug) throws SQLException {
        String content = scalar(connection, "SELECT content FROM assistant.knowledge_runtime_document WHERE release_id=? AND slug=?", release, slug);
        assertThat(content).as("runtime document %s exists", slug).isNotBlank();
        return content.toLowerCase(java.util.Locale.ROOT);
    }

    private static String releaseFingerprint(Connection connection, UUID release) throws SQLException {
        return fingerprint(connection, "SELECT * FROM assistant.knowledge_release WHERE id=?", release);
    }

    private static String runtimeFingerprint(Connection connection, UUID release) throws SQLException {
        return fingerprint(connection, "SELECT * FROM assistant.knowledge_runtime_document WHERE release_id=?", release);
    }

    private static String authoringFingerprint(Connection connection, UUID document) throws SQLException {
        return fingerprint(connection, """
                SELECT to_jsonb(d) AS document,(SELECT jsonb_agg(to_jsonb(r) ORDER BY r.version)
                  FROM assistant.knowledge_document_revision r WHERE r.document_id=d.id) AS revisions
                  FROM assistant.knowledge_document d WHERE d.id=?
                """, document);
    }

    private static String fingerprint(Connection connection, String rows, Object... parameters) throws SQLException {
        return scalar(connection, "SELECT encode(thesis.digest(COALESCE(string_agg(to_jsonb(f)::text,E'\\n' ORDER BY to_jsonb(f)::text),''),'sha256'),'hex') FROM (" + rows + ") f", parameters);
    }

    private static int count(Connection connection, String sql, Object... parameters) throws SQLException {
        return Integer.parseInt(scalar(connection, sql, parameters));
    }

    private static String scalar(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(connection, statement, parameters);
            try (var rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    private static void execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(connection, statement, parameters);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private static void bind(Connection connection, PreparedStatement statement, Object[] parameters) throws SQLException {
        for (int index=0; index<parameters.length; index++) {
            if (parameters[index] instanceof UUID[] ids) statement.setArray(index+1, connection.createArrayOf("uuid", ids));
            else statement.setObject(index+1, parameters[index]);
        }
    }

    private static Fixture fixture(String scenario) throws SQLException {
        String url = required("CAMPUSCORE_POLICY_MIGRATION_" + scenario + "_URL");
        URI target = URI.create(url.replaceFirst("^jdbc:", ""));
        assertThat(target.getScheme()).isEqualTo("postgresql");
        assertThat(target.getHost()).isIn("localhost", "127.0.0.1", "::1");
        assertThat(target.getUserInfo()).isNull();
        assertThat(target.getPath()).matches("/policy_migration_test_[a-z0-9_]+");
        Fixture fixture = new Fixture(url, required("CAMPUSCORE_POLICY_MIGRATION_USER"), required("CAMPUSCORE_POLICY_MIGRATION_PASSWORD"));
        try (Connection connection = fixture.connect()) {
            assertThat(scalar(connection, "SELECT current_database()")).isEqualTo(target.getPath().substring(1));
            assertThat(scalar(connection, "SELECT to_regclass('thesis.flyway_schema_history')::text"))
                    .as("caller creates a distinct fresh disposable database for each case/run").isNull();
        }
        return fixture;
    }

    private static String required(String variable) {
        String value = System.getenv(variable);
        if (value == null || value.isBlank()) throw new IllegalStateException("Required disposable test setting: " + variable);
        return value;
    }

    private record Fixture(String url, String user, String password) {
        Connection connect() throws SQLException { return DriverManager.getConnection(url,user,password); }
        Flyway flyway(String version) {
            return Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration")
                    .createSchemas(true).defaultSchema("thesis").schemas("thesis").cleanDisabled(true)
                    .target(MigrationVersion.fromVersion(version)).load();
        }
        void migrate(String version) { flyway(version).migrate(); }
    }
}
