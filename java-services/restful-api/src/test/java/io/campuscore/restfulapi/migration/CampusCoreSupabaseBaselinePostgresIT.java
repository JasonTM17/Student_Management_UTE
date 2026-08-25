package io.campuscore.restfulapi.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** PostgreSQL proof that the hosted baseline is V21-equivalent and data-minimal. */
class CampusCoreSupabaseBaselinePostgresIT {

    private static final String APPLICATION_SCHEMAS =
            "'campuscore_auth','academic','thesis','assistant','engagement','notifications'";
    private static final String MANAGED_SCHEMAS =
            "'auth','storage','realtime','supabase_migrations'";

    @Test
    @EnabledIfEnvironmentVariable(
            named = "CAMPUSCORE_SUPABASE_SOURCE_POSTGRES_URL",
            matches = "jdbc:postgresql:.+")
    void baselineMatchesFreshV21WithoutTouchingManagedSchemasOrCopyingRows() throws Exception {
        String sourceUrl = System.getenv("CAMPUSCORE_SUPABASE_SOURCE_POSTGRES_URL");
        String baselineUrl = required("CAMPUSCORE_SUPABASE_BASELINE_POSTGRES_URL");
        String user = valueOr("CAMPUSCORE_SUPABASE_POSTGRES_USER", "postgres");
        String password = valueOr("CAMPUSCORE_SUPABASE_POSTGRES_PASSWORD", "postgres");

        Flyway source = configured(sourceUrl, user, password, "classpath:db/migration");
        source.migrate();
        assertTrue(source.validateWithResult().validationSuccessful);

        List<String> sourceSignature;
        try (Connection connection = DriverManager.getConnection(sourceUrl, user, password)) {
            sourceSignature = signature(connection, APPLICATION_SCHEMAS);
            assertEquals("21", scalar(connection,
                    "SELECT version FROM thesis.flyway_schema_history"
                            + " WHERE success ORDER BY installed_rank DESC LIMIT 1"));
        }

        List<String> managedBefore;
        try (Connection connection = DriverManager.getConnection(baselineUrl, user, password)) {
            prepareManagedSentinels(connection);
            managedBefore = signature(connection, MANAGED_SCHEMAS);
        }

        Flyway baseline = configured(
                baselineUrl,
                user,
                password,
                "classpath:db/supabase-baseline");
        baseline.migrate();
        assertTrue(baseline.validateWithResult().validationSuccessful);

        try (Connection connection = DriverManager.getConnection(baselineUrl, user, password)) {
            List<String> baselineSignature = signature(connection, APPLICATION_SCHEMAS);
            assertEquals(sourceSignature, baselineSignature,
                    () -> signatureDelta(sourceSignature, baselineSignature));
            assertEquals(managedBefore, signature(connection, MANAGED_SCHEMAS));
            assertEquals("1", scalar(connection,
                    "SELECT COUNT(*)::text FROM thesis.flyway_schema_history"
                            + " WHERE version='20' AND type='SQL_BASELINE' AND success"));
            assertEquals("1", scalar(connection,
                    "SELECT COUNT(*)::text FROM thesis.flyway_schema_history"
                            + " WHERE version='21' AND type='SQL' AND success"));
            assertEquals("0", scalar(connection,
                    "SELECT COUNT(*)::text FROM thesis.flyway_schema_history"
                            + " WHERE version IS NOT NULL AND version NOT IN ('20','21')"));
            assertEquals(0L, totalApplicationRows(connection));
            assertEquals("0", scalar(connection,
                    "SELECT COUNT(*)::text FROM information_schema.tables"
                            + " WHERE table_schema='auth'"
                            + " AND table_name IN ('User','Role','Permission','UserRole','RolePermission',"
                            + "'Session','AuthChallenge','AuthRateLimitBucket')"));
        }
    }

    private static Flyway configured(String url, String user, String password, String... locations) {
        return Flyway.configure()
                .dataSource(url, user, password)
                .locations(locations)
                .createSchemas(true)
                .defaultSchema("thesis")
                .schemas("thesis")
                .cleanDisabled(true)
                .load();
    }

    private static void prepareManagedSentinels(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            for (String schema : List.of("auth", "storage", "realtime", "supabase_migrations")) {
                statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
                statement.execute("CREATE TABLE IF NOT EXISTS " + schema
                        + ".platform_sentinel (id INTEGER PRIMARY KEY, marker VARCHAR(40) NOT NULL)");
                statement.execute("INSERT INTO " + schema
                        + ".platform_sentinel VALUES (1, 'preserve') ON CONFLICT (id) DO NOTHING");
            }
        }
    }

    private static List<String> signature(Connection connection, String schemas) throws Exception {
        String sql = """
                SELECT signature FROM (
                    SELECT 'SCHEMA|' || n.nspname AS signature
                    FROM pg_namespace n
                    WHERE n.nspname IN (%1$s)
                    UNION ALL
                    SELECT 'REL|' || n.nspname || '|' || c.relname || '|' || c.relkind::text
                    FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE n.nspname IN (%1$s) AND c.relkind IN ('r','p','v','m','S')
                    UNION ALL
                    SELECT 'COL|' || n.nspname || '|' || c.relname || '|'
                        || (row_number() OVER (PARTITION BY c.oid ORDER BY a.attnum))::text || '|'
                        || a.attname || '|' || pg_catalog.format_type(a.atttypid,a.atttypmod) || '|'
                        || a.attnotnull::text || '|' || COALESCE(pg_get_expr(d.adbin,d.adrelid),'')
                    FROM pg_attribute a
                    JOIN pg_class c ON c.oid=a.attrelid
                    JOIN pg_namespace n ON n.oid=c.relnamespace
                    LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
                    WHERE n.nspname IN (%1$s) AND a.attnum > 0 AND NOT a.attisdropped
                        AND c.relkind IN ('r','p','v','m','S')
                    UNION ALL
                    SELECT 'CON|' || n.nspname || '|' || c.relname || '|' || x.conname || '|'
                        || x.contype::text || '|' || pg_get_constraintdef(x.oid, true)
                    FROM pg_constraint x
                    JOIN pg_class c ON c.oid=x.conrelid
                    JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE n.nspname IN (%1$s)
                    UNION ALL
                    SELECT 'IDX|' || schemaname || '|' || tablename || '|' || indexname || '|' || indexdef
                    FROM pg_indexes WHERE schemaname IN (%1$s)
                    UNION ALL
                    SELECT 'VIEW|' || n.nspname || '|' || c.relname || '|'
                        || pg_get_viewdef(c.oid, true)
                    FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE n.nspname IN (%1$s) AND c.relkind IN ('v','m')
                ) signatures ORDER BY signature
                """.formatted(schemas);
        List<String> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                values.add(canonicalSignature(result.getString(1)));
            }
        }
        return values;
    }

    private static String canonicalSignature(String value) {
        return value
                // pg_dump restores an enum-style ANY array with equivalent explicit element casts.
                .replaceAll("\\(('(?:''|[^'])*')::character varying\\)::text", "$1::character varying")
                .replace("::character varying::text", "::character varying")
                .replaceAll(
                        "ANY \\(\\(?(ARRAY\\[[^]]*])\\)?::text\\[\\]\\)",
                        "ANY ($1)")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static long totalApplicationRows(Connection connection) throws Exception {
        List<String[]> tables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT table_schema, table_name FROM information_schema.tables"
                        + " WHERE table_type='BASE TABLE' AND table_schema IN (" + APPLICATION_SCHEMAS + ")"
                        + " AND NOT (table_schema='thesis' AND table_name='flyway_schema_history')"
                        + " ORDER BY table_schema, table_name");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                tables.add(new String[]{result.getString(1), result.getString(2)});
            }
        }
        long total = 0;
        for (String[] table : tables) {
            total += Long.parseLong(scalar(connection,
                    "SELECT COUNT(*)::text FROM " + identifier(table[0]) + "." + identifier(table[1])));
        }
        return total;
    }

    private static String signatureDelta(List<String> expected, List<String> actual) {
        List<String> missing = expected.stream().filter(value -> !actual.contains(value)).limit(20).toList();
        List<String> unexpected = actual.stream().filter(value -> !expected.contains(value)).limit(20).toList();
        return "schema signature differs; missing=" + missing + "; unexpected=" + unexpected;
    }

    private static String identifier(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String scalar(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getString(1) : null;
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the Supabase baseline test");
        }
        return value;
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
