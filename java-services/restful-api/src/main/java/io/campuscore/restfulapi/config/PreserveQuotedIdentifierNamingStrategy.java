package io.campuscore.restfulapi.config;

import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

/**
 * Hibernate physical naming strategy that honours explicitly quoted identifiers verbatim.
 *
 * <p>Flyway owns this database and two naming families coexist in it: the thesis/academic
 * schemas use snake_case tables ({@code thesis.thesis_topic}), while the enterprise
 * announcement and article tables were migrated as quoted camelCase objects
 * ({@code engagement."ArticleCategory"} with columns like {@code "nameVi"}). Hibernate's
 * default {@link CamelCaseToUnderscoresNamingStrategy} rewrites <em>every</em> identifier,
 * quoted or not, so an entity that declares {@code @Table(name = "\"ArticleCategory\"")}
 * still produced {@code engagement."article_category"} and every read of the mapped article
 * routes failed with "table not found" (HTTP 500).
 *
 * <p>This strategy is additive: an identifier that the mapping quoted is passed through
 * unchanged, and every unquoted identifier keeps going through the default camel-case to
 * underscore conversion, so all snake_case mappings resolve exactly as before.
 */
public class PreserveQuotedIdentifierNamingStrategy extends CamelCaseToUnderscoresNamingStrategy {

    @Override
    public Identifier toPhysicalCatalogName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return verbatimWhenQuoted(name, super.toPhysicalCatalogName(name, jdbcEnvironment));
    }

    @Override
    public Identifier toPhysicalSchemaName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return verbatimWhenQuoted(name, super.toPhysicalSchemaName(name, jdbcEnvironment));
    }

    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return verbatimWhenQuoted(name, super.toPhysicalTableName(name, jdbcEnvironment));
    }

    @Override
    public Identifier toPhysicalSequenceName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return verbatimWhenQuoted(name, super.toPhysicalSequenceName(name, jdbcEnvironment));
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return verbatimWhenQuoted(name, super.toPhysicalColumnName(name, jdbcEnvironment));
    }

    /**
     * Keeps a quoted identifier as the mapping wrote it and falls back to the default
     * conversion otherwise.
     *
     * @param name identifier exactly as declared by the JPA mapping, possibly {@code null}
     * @param converted identifier produced by the default camel-case to underscore strategy
     * @return {@code name} when the mapping quoted it, {@code converted} otherwise
     */
    private static Identifier verbatimWhenQuoted(Identifier name, Identifier converted) {
        return name == null || !name.isQuoted() ? converted : name;
    }
}
