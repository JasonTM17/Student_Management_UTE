package io.campuscore.restfulapi;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Proves the JPA entity layer matches the real PostgreSQL schema end to end.
 *
 * <p>The test migrates the disposable target database with the same Flyway
 * configuration the application uses, then boots a bare Hibernate
 * {@link SessionFactory} with {@code hibernate.hbm2ddl.auto=validate} over
 * every discovered {@code @Entity} class — the same naming strategy and
 * default-schema/time-zone settings as {@code application.yml}. Any wrong
 * table name, missing column, or incompatible column type fails the factory
 * build.
 *
 * <p>It then asserts the set of mapped {@code schema.table} names equals the
 * set of base tables in the schema minus {@link #EXCLUDED_TABLES}, so a table
 * that gains no entity (or an entity pointing at a table that no longer
 * exists) fails here instead of at application boot.
 *
 * <p><b>Environment contract</b> (opt-in, same as the other
 * {@code *PostgresIT} classes): runs ONLY when
 * {@code CAMPUSCORE_E2E_POSTGRES_URL} is a {@code jdbc:postgresql:} URL
 * pointing at a disposable database — real Flyway migrations run inside this
 * test. Optional {@code CAMPUSCORE_E2E_POSTGRES_USER} /
 * {@code CAMPUSCORE_E2E_POSTGRES_PASSWORD} (defaults
 * {@code campuscore}/{@code postgres}).
 */
@EnabledIf("postgresTargetConfigured")
class SchemaEntityMappingPostgresIT {

    /**
     * Tables that intentionally have no entity: {@code flyway_schema_history}
     * is Flyway-internal bookkeeping, not application data. Views
     * ({@code campuscore_auth."Lecturer"}, {@code campuscore_auth."Student"})
     * are never base tables and never appear in the schema set below.
     */
    private static final Set<String> EXCLUDED_TABLES = Set.of(
            "thesis.flyway_schema_history");

    private static final String ROOT = "io/campuscore/restfulapi";
    private static final byte[] ENTITY_ANNOTATION =
            "Ljakarta/persistence/Entity;".getBytes();

    static boolean postgresTargetConfigured() {
        String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_URL");
        return url != null && url.startsWith("jdbc:postgresql:");
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @Test
    void everyEntityMappingValidatesAgainstTheMigratedSchema() throws Exception {
        String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_URL");
        String user = valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "campuscore");
        String pass = valueOr("CAMPUSCORE_E2E_POSTGRES_PASSWORD", "postgres");

        Flyway.configure()
                .dataSource(url, user, pass)
                .locations("classpath:db/migration")
                .defaultSchema("thesis")
                .schemas("thesis", "academic", "campuscore_auth")
                .createSchemas(true)
                .load()
                .migrate();

        List<Class<?>> entities = entityClasses();
        assertThat(entities).isNotEmpty();

        Properties props = new Properties();
        props.setProperty("hibernate.connection.url", url);
        props.setProperty("hibernate.connection.username", user);
        props.setProperty("hibernate.connection.password", pass);
        props.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.setProperty("hibernate.hbm2ddl.auto", "validate");
        props.setProperty("hibernate.physical_naming_strategy",
                "io.campuscore.restfulapi.config.jpa.PreserveQuotedIdentifierNamingStrategy");
        props.setProperty("hibernate.default_schema", "thesis");
        props.setProperty("hibernate.jdbc.time_zone", "UTC");

        Configuration configuration = new Configuration().setProperties(props);
        entities.forEach(configuration::addAnnotatedClass);
        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            assertThat(sessionFactory).isNotNull();
        }

        Set<String> mappedTables = new TreeSet<>();
        for (Class<?> entity : entities) {
            Table table = entity.getAnnotation(Table.class);
            String tableName = table.name().replace("\"", "");
            mappedTables.add(table.schema() + "." + tableName);
        }

        Set<String> schemaTables = new TreeSet<>();
        try (Connection connection = DriverManager.getConnection(url, user, pass);
                ResultSet rs = connection.createStatement().executeQuery(
                        "SELECT table_schema || '.' || table_name FROM information_schema.tables "
                                + "WHERE table_type = 'BASE TABLE' "
                                + "AND table_schema NOT IN ('pg_catalog', 'information_schema')")) {
            while (rs.next()) {
                schemaTables.add(rs.getString(1));
            }
        }
        schemaTables.removeAll(EXCLUDED_TABLES);

        assertThat(mappedTables)
                .as("mapped @Entity tables must equal schema base tables minus %s", EXCLUDED_TABLES)
                .isEqualTo(schemaTables);
    }

    private static List<Class<?>> entityClasses() throws IOException {
        Path classesDir = classesDirectory();
        List<String> names;
        try (Stream<Path> paths = Files.walk(classesDir.resolve(ROOT))) {
            names = paths.filter(p -> p.toString().endsWith(".class"))
                    .map(classesDir::relativize)
                    .map(p -> p.toString().replace('\\', '/').replace(".class", ""))
                    .filter(n -> !n.contains("$"))
                    .filter(n -> n.startsWith(ROOT + "/"))
                    .filter(n -> containsMarker(classesDir, n))
                    .toList();
        }
        List<Class<?>> classes = new java.util.ArrayList<>();
        for (String n : names) {
            try {
                Class<?> c = Class.forName(n.replace('/', '.'), false,
                        SchemaEntityMappingPostgresIT.class.getClassLoader());
                if (c.isAnnotationPresent(Entity.class)) {
                    classes.add(c);
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("entity class not loadable: " + n, e);
            }
        }
        return classes;
    }

    private static boolean containsMarker(Path classesDir, String binaryName) {
        try {
            return contains(Files.readAllBytes(classesDir.resolve(binaryName + ".class")),
                    ENTITY_ANNOTATION);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static Path classesDirectory() {
        try {
            Path testClasses = Path.of(SchemaEntityMappingPostgresIT.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path classes = testClasses.resolveSibling("classes");
            if (Files.isDirectory(classes)) {
                return classes;
            }
            return testClasses;
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
