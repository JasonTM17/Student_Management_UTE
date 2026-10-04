package io.campuscore.restfulapi.thesis.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Forward-only proof on distinct caller-created local databases. Never clean, reset or drop. */
@EnabledIfEnvironmentVariable(named = "CAMPUSCORE_CERTIFICATE_MIGRATION_ENABLED", matches = "true")
class StudentAffairsCertificateCorrectionMigrationPostgresIT {
    private static final UUID BASE = UUID.fromString("00000000-0000-0000-0000-000000000098");
    private static final UUID NEXT = UUID.fromString("00000000-0000-0000-0000-000000000099");
    // Independent, fixed V41 identities; never read the migration's eligibility table as an oracle.
    private static final UUID VI = UUID.fromString("dcd11df4-2cb3-21fb-f9b5-638f12681c6b");
    private static final UUID EN = UUID.fromString("51b2dfd2-722d-630c-fe0b-d6ba340e1aa9");
    private static final String SOURCE = "campuscore-student-affairs-corrected-v99";

    @Test
    void cleanInstallationPublishesBothCorrectionsAndValidates() throws Exception {
        Fixture fixture = fixture("CLEAN");
        fixture.migrate("99");
        try (Connection connection = fixture.connect()) {
            assertHistory(connection, fixture);
            assertThat(active(connection)).isEqualTo(NEXT.toString());
            assertCorrections(connection, VI, EN);
            assertProjection(connection, VI, EN);
        }
    }

    @Test
    void normal098UpgradePreservesHistoryAndRerunIsStable() throws Exception {
        Fixture fixture = fixture("LOCAL");
        fixture.migrate("98");
        try (Connection connection = fixture.connect()) {
            assertThat(active(connection)).isEqualTo(BASE.toString());
            assertThat(scalar(connection, "SELECT content FROM assistant.knowledge_runtime_document WHERE release_id=? AND source_id=?", BASE, VI.toString()))
                    .contains("1 đến 2 ngày");
            String history = historical(connection);
            String unrelated = fingerprint(connection, "SELECT to_jsonb(d) AS document,(SELECT jsonb_agg(to_jsonb(r) ORDER BY version) FROM assistant.knowledge_document_revision r WHERE r.document_id=d.id) AS revisions FROM assistant.knowledge_document d WHERE d.id<>? AND d.id<>?", VI, EN);
            String originalPayloads = fingerprint(connection, "SELECT id,document_id,version,domain,locale,slug,title,content,source,priority,created_by,reviewed_by,published_at FROM assistant.knowledge_document_revision WHERE document_id IN (?,?) AND version=1", VI, EN);
            fixture.migrate("99");
            assertHistory(connection, fixture);
            assertThat(historical(connection)).isEqualTo(history);
            assertThat(fingerprint(connection, "SELECT to_jsonb(d) AS document,(SELECT jsonb_agg(to_jsonb(r) ORDER BY version) FROM assistant.knowledge_document_revision r WHERE r.document_id=d.id) AS revisions FROM assistant.knowledge_document d WHERE d.id<>? AND d.id<>?", VI, EN)).isEqualTo(unrelated);
            assertThat(fingerprint(connection, "SELECT id,document_id,version,domain,locale,slug,title,content,source,priority,created_by,reviewed_by,published_at FROM assistant.knowledge_document_revision WHERE document_id IN (?,?) AND version=1", VI, EN)).isEqualTo(originalPayloads);
            assertCorrections(connection, VI, EN);
            assertProjection(connection, VI, EN);
            String stable = fingerprint(connection, "SELECT * FROM assistant.knowledge_runtime_document WHERE release_id=?", NEXT);
            String stableRelease = fingerprint(connection, "SELECT * FROM assistant.knowledge_release WHERE id=?", NEXT);
            fixture.migrate("99");
            assertThat(fingerprint(connection, "SELECT * FROM assistant.knowledge_runtime_document WHERE release_id=?", NEXT)).isEqualTo(stable);
            assertThat(fingerprint(connection, "SELECT * FROM assistant.knowledge_release WHERE id=?", NEXT)).isEqualTo(stableRelease);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONTENT", "REVISION_CONTENT", "DOCUMENT_METADATA", "REVISION_METADATA", "STATUS", "DRAFT", "PUBLISHED"})
    void humanAndMetadataDriftRemainExactWhileTheOtherSeedIsCorrected(String variant) throws Exception {
        Fixture fixture = fixture("DRIFT_" + variant);
        fixture.migrate("98");
        try (Connection connection = fixture.connect()) {
            switch (variant) {
                case "CONTENT" -> execute(connection, "UPDATE assistant.knowledge_document SET content='Human certificate wording' WHERE id=?", VI);
                case "REVISION_CONTENT" -> execute(connection, "UPDATE assistant.knowledge_document_revision SET content='Human revision wording' WHERE document_id=? AND version=1", VI);
                case "DOCUMENT_METADATA" -> execute(connection, "UPDATE assistant.knowledge_document SET priority=4 WHERE id=?", VI);
                case "REVISION_METADATA" -> execute(connection, "UPDATE assistant.knowledge_document_revision SET reviewed_by='human-reviewer' WHERE document_id=? AND version=1", VI);
                case "STATUS" -> execute(connection, "UPDATE assistant.knowledge_document SET active=FALSE WHERE id=?", VI);
                case "DRAFT", "PUBLISHED" -> {
                    if (variant.equals("PUBLISHED")) execute(connection, "UPDATE assistant.knowledge_document_revision SET state='ARCHIVED' WHERE document_id=? AND version=1", VI);
                    execute(connection, """
                            INSERT INTO assistant.knowledge_document_revision
                              (id,document_id,version,state,domain,locale,slug,title,content,source,priority,created_by,reviewed_by,published_at)
                            SELECT ?,id,2,?,domain,locale,slug,'Human title','Human application guidance',source,priority,'human-author','human-reviewer',CURRENT_TIMESTAMP
                              FROM assistant.knowledge_document WHERE id=?
                            """, UUID.randomUUID(), variant, VI);
                }
                default -> throw new IllegalArgumentException(variant);
            }
            String preserved = authoring(connection, VI);
            String history = historical(connection);
            fixture.migrate("99");
            assertThat(authoring(connection, VI)).isEqualTo(preserved);
            assertThat(historical(connection)).isEqualTo(history);
            assertCorrections(connection, EN);
            assertProjection(connection, EN);
        }
    }

    @Test
    void inFlightPublisherIsWaitedForBeforeEligibilityWithoutLockInversion() throws Exception {
        Fixture fixture = fixture("CONCURRENT");
        fixture.migrate("98");
        try (Connection observer = fixture.connect(); Connection writer = fixture.connect(); var executor = Executors.newSingleThreadExecutor()) {
            writer.setAutoCommit(false);
            execute(writer, "UPDATE assistant.knowledge_document SET priority=4 WHERE id=?", VI);
            int writerPid = count(writer, "SELECT pg_backend_pid()");
            var migration = executor.submit(() -> fixture.migrate("99"));
            try {
                awaitBlocked(observer, writerPid);
                execute(writer, "SET LOCAL lock_timeout='2s'");
                scalar(writer, "SELECT active_release_id::text FROM assistant.knowledge_runtime_state WHERE singleton=TRUE FOR UPDATE");
                writer.commit();
                String preserved = authoring(observer, VI);
                migration.get(30, TimeUnit.SECONDS);
                assertThat(authoring(observer, VI)).isEqualTo(preserved);
                assertCorrections(observer, EN);
                assertProjection(observer, EN);
            } finally {
                writer.rollback();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"FOREIGN", "NEWER"})
    void foreignOrNewerAuthorityIsNeverReplaced(String kind) throws Exception {
        Fixture fixture = fixture(kind);
        fixture.migrate("98");
        try (Connection connection = fixture.connect()) {
            UUID external = UUID.randomUUID();
            execute(connection, """
                    INSERT INTO assistant.knowledge_release
                      (id,corpus_version,corpus_hash,row_count,source,status,manifest,created_by,activated_at,previous_release_id)
                    VALUES (?, ?, encode(thesis.digest('externally governed certificate text','sha256'),'hex'),1,?,'PUBLISHED','{}','independent-office',CURRENT_TIMESTAMP,?)
                    """, external, "certificate-test-" + kind, kind.equals("FOREIGN") ? "SUPABASE" : "MANUAL", BASE);
            execute(connection, """
                    INSERT INTO assistant.knowledge_runtime_document
                      (release_id,source_id,version,domain,slug,locale,title,content,source,priority,active,visibility)
                    VALUES (?,'external-certificate-source',3,'POLICY','external-certificate','en','Official certificate guidance','Externally approved instructions','independent-office',2,TRUE,'PUBLIC')
                    """, external);
            execute(connection, "UPDATE assistant.knowledge_runtime_state SET active_release_id=? WHERE singleton=TRUE", external);
            String state = fingerprint(connection, "SELECT * FROM assistant.knowledge_runtime_state");
            String history = historical(connection);
            fixture.migrate("99");
            assertThat(fingerprint(connection, "SELECT * FROM assistant.knowledge_runtime_state")).isEqualTo(state);
            assertThat(historical(connection)).isEqualTo(history);
            assertThat(count(connection, "SELECT COUNT(*) FROM assistant.knowledge_release WHERE id=?", NEXT)).isZero();
            assertCorrections(connection, VI, EN);
            assertHistory(connection, fixture);
        }
    }

    @Test
    void changedBasePayloadIsNotOverlaidEvenWhenOriginalAuthoringIsEligible() throws Exception {
        Fixture fixture = fixture("BASE_DRIFT");
        fixture.migrate("98");
        try (Connection connection = fixture.connect()) {
            // A deliberately different immutable fixture is an independent negative input.
            execute(connection, "UPDATE assistant.knowledge_runtime_document SET priority=6 WHERE release_id=? AND source_id=?", BASE, VI.toString());
            String history = historical(connection);
            fixture.migrate("99");
            assertThat(historical(connection)).isEqualTo(history);
            assertCorrections(connection, VI, EN);
            assertProjection(connection, EN);
        }
    }

    private static void assertCorrections(Connection connection, UUID... ids) throws SQLException {
        for (UUID id : ids) {
            assertThat(count(connection, "SELECT COUNT(*) FROM assistant.knowledge_document_revision WHERE document_id=? AND version=1 AND state='ARCHIVED'", id)).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM assistant.knowledge_document_revision WHERE document_id=? AND version=2 AND state='PUBLISHED' AND source=? AND created_by='system-migration' AND reviewed_by='system-migration'", id, SOURCE)).isEqualTo(1);
            String content = scalar(connection, "SELECT content FROM assistant.knowledge_document_revision WHERE document_id=? AND version=2", id);
            if (id.equals(VI)) assertThat(content).contains("chưa được cấp", "không gửi", "không theo dõi", "kênh tiếp nhận", "BHYT");
            else assertThat(content).contains("unissued", "does not submit", "does not track", "receiving channel", "health insurance");
            assertThat(content).doesNotContain("1 đến 2", "1-2", "5-7", "100%", "Cổng dịch vụ sinh viên trực tuyến");
        }
    }

    private static void assertProjection(Connection connection, UUID... corrected) throws SQLException {
        assertThat(active(connection)).isEqualTo(NEXT.toString());
        UUID[] ids = corrected;
        // V99 overlays the …098 map without adding or removing rows, so the
        // projected row count must equal the base release's on every database.
        assertThat(count(connection, "SELECT COUNT(*) FROM assistant.knowledge_runtime_document WHERE release_id=?", NEXT))
                .isEqualTo(count(connection, "SELECT COUNT(*) FROM assistant.knowledge_runtime_document WHERE release_id=?", BASE));
        assertThat(scalar(connection, "SELECT previous_release_id::text FROM assistant.knowledge_release WHERE id=?", NEXT)).isEqualTo(BASE.toString());
        assertThat(scalar(connection, "SELECT string_agg(source_id,E'\\n' ORDER BY source_id) FROM assistant.knowledge_runtime_document WHERE release_id=?", NEXT))
                .isEqualTo(scalar(connection, "SELECT string_agg(source_id,E'\\n' ORDER BY source_id) FROM assistant.knowledge_runtime_document WHERE release_id=?", BASE));
        assertThat(count(connection, "SELECT COUNT(*)-COUNT(DISTINCT source_id) FROM assistant.knowledge_runtime_document WHERE release_id=?", NEXT)).isZero();
        String unchangedSql = "SELECT to_jsonb(p)-'release_id' FROM assistant.knowledge_runtime_document p WHERE release_id=? AND NOT(source_id::uuid=ANY(?::uuid[]))";
        assertThat(fingerprint(connection, unchangedSql, NEXT, ids)).isEqualTo(fingerprint(connection, unchangedSql, BASE, ids));
        for (UUID id : ids) {
            assertThat(count(connection, """
                    SELECT COUNT(*) FROM assistant.knowledge_runtime_document p
                    JOIN assistant.knowledge_document_revision r ON r.document_id=? AND r.version=2 AND r.state='PUBLISHED'
                    JOIN assistant.knowledge_document d ON d.id=r.document_id
                    WHERE p.release_id=? AND p.source_id=? AND
                      ROW(p.revision_id,p.version,p.domain,p.slug,p.locale,p.title,p.content,p.source,p.priority,p.active,p.visibility,p.published_at)
                      IS NOT DISTINCT FROM ROW(r.id,r.version,r.domain,r.slug,r.locale,r.title,r.content,r.source,r.priority,d.active,d.visibility,r.published_at)
                    """, id, NEXT, id.toString())).isEqualTo(1);
        }
        assertThat(count(connection, """
                WITH expected AS (
                  SELECT COUNT(*)::integer AS n,
                    encode(thesis.digest(string_agg(concat_ws('|',source_id,COALESCE(revision_id::text,''),version::text,domain,slug,locale,title,content,source,priority::text,active::text,visibility),E'\n' ORDER BY source_id),'sha256'),'hex') AS hash,
                    jsonb_agg(jsonb_build_object('sourceId',source_id,'domain',domain,'slug',slug,'locale',locale) ORDER BY source_id) AS docs
                    FROM assistant.knowledge_runtime_document WHERE release_id=?
                ) SELECT COUNT(*) FROM assistant.knowledge_release r CROSS JOIN expected e WHERE r.id=?
                  AND r.row_count=e.n AND r.corpus_hash=e.hash AND r.manifest->>'sha256'=e.hash
                  AND (r.manifest->>'rowCount')::integer=e.n AND r.manifest->'documents'=e.docs
                """, NEXT, NEXT)).isEqualTo(1);
    }

    private static void assertHistory(Connection connection, Fixture fixture) throws SQLException {
        assertThat(scalar(connection, "SELECT version FROM thesis.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")).isEqualTo("99");
        assertThat(count(connection, "SELECT checksum FROM thesis.flyway_schema_history WHERE version='95' AND success")).isEqualTo(359601249);
        assertThat(count(connection, "SELECT checksum FROM thesis.flyway_schema_history WHERE version='96' AND success")).isEqualTo(1556597148);
        assertThat(fixture.flyway("99").validateWithResult().validationSuccessful).isTrue();
    }

    private static String historical(Connection connection) throws SQLException {
        return fingerprint(connection, "SELECT to_jsonb(r) AS release,(SELECT jsonb_agg(to_jsonb(p) ORDER BY source_id) FROM assistant.knowledge_runtime_document p WHERE p.release_id=r.id) AS projection FROM assistant.knowledge_release r WHERE r.id<>?", NEXT);
    }

    private static String authoring(Connection connection, UUID id) throws SQLException {
        return fingerprint(connection, "SELECT to_jsonb(d) AS document,(SELECT jsonb_agg(to_jsonb(r) ORDER BY version) FROM assistant.knowledge_document_revision r WHERE r.document_id=d.id) AS revisions FROM assistant.knowledge_document d WHERE d.id=?", id);
    }

    private static String active(Connection connection) throws SQLException {
        return scalar(connection, "SELECT active_release_id::text FROM assistant.knowledge_runtime_state WHERE singleton=TRUE");
    }

    private static void awaitBlocked(Connection observer, int blocker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (count(observer, "SELECT COUNT(*) FROM pg_stat_activity WHERE datname=current_database() AND ?=ANY(pg_blocking_pids(pid))", blocker) > 0) return;
            Thread.sleep(25);
        }
        throw new AssertionError("migration must actually wait for the held document lock");
    }

    private static String fingerprint(Connection connection, String query, Object... args) throws SQLException {
        return scalar(connection, "SELECT encode(thesis.digest(COALESCE(string_agg(to_jsonb(f)::text,E'\\n' ORDER BY to_jsonb(f)::text),''),'sha256'),'hex') FROM (" + query + ") f", args);
    }

    private static int count(Connection connection, String query, Object... args) throws SQLException {
        return Integer.parseInt(scalar(connection, query, args));
    }

    private static String scalar(Connection connection, String query, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            bind(connection, statement, args);
            try (var rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    private static void execute(Connection connection, String query, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            bind(connection, statement, args);
            statement.execute();
        }
    }

    private static void bind(Connection connection, PreparedStatement statement, Object[] args) throws SQLException {
        for (int i=0;i<args.length;i++) {
            if (args[i] instanceof UUID[] ids) statement.setArray(i+1,connection.createArrayOf("uuid",ids));
            else statement.setObject(i+1,args[i]);
        }
    }

    private static Fixture fixture(String name) throws SQLException {
        String url = required("CAMPUSCORE_CERTIFICATE_MIGRATION_" + name + "_URL");
        URI target = URI.create(url.replaceFirst("^jdbc:", ""));
        assertThat(target.getScheme()).isEqualTo("postgresql");
        assertThat(target.getHost()).isIn("localhost", "127.0.0.1", "::1");
        assertThat(target.getUserInfo()).isNull();
        assertThat(target.getPath()).matches("/policy_migration_test_[a-z0-9_]+");
        Fixture fixture = new Fixture(url,required("CAMPUSCORE_CERTIFICATE_MIGRATION_USER"),required("CAMPUSCORE_CERTIFICATE_MIGRATION_PASSWORD"));
        try (Connection connection = fixture.connect()) {
            assertThat(scalar(connection,"SELECT current_database()")).isEqualTo(target.getPath().substring(1));
            assertThat(scalar(connection,"SELECT to_regclass('thesis.flyway_schema_history')::text")).isNull();
        }
        return fixture;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing disposable test setting: " + name);
        return value;
    }

    private record Fixture(String url,String user,String password) {
        Connection connect() throws SQLException { return DriverManager.getConnection(url,user,password); }
        Flyway flyway(String version) {
            return Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration")
                    .createSchemas(true).defaultSchema("thesis").schemas("thesis").cleanDisabled(true)
                    .target(MigrationVersion.fromVersion(version)).load();
        }
        void migrate(String version) { flyway(version).migrate(); }
    }
}
