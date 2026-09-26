package io.campuscore.restfulapi.thesis.assistant;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/** Keeps assistant RLS traffic on its own bounded PostgreSQL login and transaction manager. */
@Configuration(proxyBeanMethods = false)
@Profile("persistence")
public class AssistantDatabaseConfiguration {
    public static final String RUNTIME_ROLE = "campuscore_assistant_runtime";
    public static final String DATA_SOURCE = "assistantDataSource";
    public static final String JDBC_TEMPLATE = "assistantNamedParameterJdbcTemplate";
    public static final String TRANSACTION_MANAGER = "assistantTransactionManager";

    /** The dedicated pool makes the primary datasource explicit so Flyway/JPA keep their existing role. */
    @Bean(name = "dataSource")
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource primaryDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean(name = "namedParameterJdbcTemplate")
    @Primary
    NamedParameterJdbcTemplate primaryNamedParameterJdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean(name = "transactionManager")
    @Primary
    @ConditionalOnMissingBean(name = "transactionManager")
    PlatformTransactionManager primaryJpaTransactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean(name = DATA_SOURCE, destroyMethod = "close")
    @Profile("!test")
    HikariDataSource assistantDataSource(
            @Value("${assistant.datasource.url:}") String url,
            @Value("${assistant.datasource.username:}") String username,
            @Value("${assistant.datasource.password:}") String password,
            @Value("${assistant.datasource.maximum-pool-size:4}") int maximumPoolSize,
            @Value("${assistant.datasource.connection-timeout-ms:15000}") long connectionTimeoutMs,
            @Value("${assistant.rls.test-mode:false}") boolean testMode) {
        if (testMode) {
            throw new IllegalStateException("Assistant RLS test mode is forbidden outside the test profile");
        }
        if (url == null || url.isBlank() || !url.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("A PostgreSQL Assistant datasource URL is required");
        }
        if (!isRuntimeLogin(username)) {
            throw new IllegalStateException("Assistant datasource must use the dedicated runtime login");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Assistant runtime login credentials are required");
        }
        if (maximumPoolSize < 1 || maximumPoolSize > 4) {
            throw new IllegalStateException("Assistant connection pool maximum must be between 1 and 4");
        }
        if (connectionTimeoutMs < 1000 || connectionTimeoutMs > 60000) {
            throw new IllegalStateException("Assistant connection timeout must be between 1 and 60 seconds");
        }
        rejectCredentialsEmbeddedInUrl(url);

        HikariConfig config = new HikariConfig();
        config.setPoolName("campuscore-assistant");
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(connectionTimeoutMs);
        config.setValidationTimeout(Math.min(3000, connectionTimeoutMs));
        config.setInitializationFailTimeout(5000);
        return new HikariDataSource(config);
    }

    /** H2 is permitted only in an explicitly active test profile with test-mode enabled. */
    @Bean(name = DATA_SOURCE)
    @Profile("test")
    DataSource testAssistantDataSource(@Qualifier("dataSource") DataSource primaryDataSource,
            @Value("${assistant.rls.test-mode:false}") boolean testMode) {
        if (!testMode) {
            throw new IllegalStateException("The test Assistant datasource requires assistant.rls.test-mode=true");
        }
        return primaryDataSource;
    }

    @Bean(name = JDBC_TEMPLATE)
    NamedParameterJdbcTemplate assistantNamedParameterJdbcTemplate(
            @Qualifier(DATA_SOURCE) DataSource assistantDataSource) {
        return new NamedParameterJdbcTemplate(assistantDataSource);
    }

    @Bean(name = TRANSACTION_MANAGER)
    PlatformTransactionManager assistantTransactionManager(
            @Qualifier(DATA_SOURCE) DataSource assistantDataSource) {
        return new DataSourceTransactionManager(assistantDataSource);
    }

    @Bean
    AssistantRlsTransactionRunner assistantRlsTransactionRunner(
            @Qualifier(TRANSACTION_MANAGER) PlatformTransactionManager assistantTransactionManager,
            @Qualifier(JDBC_TEMPLATE) NamedParameterJdbcTemplate assistantJdbc,
            @Value("${assistant.rls.test-mode:false}") boolean testMode) {
        return new AssistantRlsTransactionRunner(assistantTransactionManager, assistantJdbc, testMode);
    }

    /**
     * Accepts the dedicated runtime login either bare or in the Supabase session-pooler
     * form {@code <role>.<project-ref>}, where the ref suffix identifies the tenant to
     * the pooler and the server strips it before authenticating. Any other spelling —
     * including the primary application credentials — is rejected.
     */
    private static boolean isRuntimeLogin(String username) {
        return username != null
                && (username.equals(RUNTIME_ROLE) || username.startsWith(RUNTIME_ROLE + "."));
    }

    private static void rejectCredentialsEmbeddedInUrl(String url) {
        int queryStart = url.indexOf('?');
        if (queryStart < 0 || queryStart == url.length() - 1) {
            return;
        }
        String query = url.substring(queryStart + 1);
        for (String parameter : query.split("[&;]")) {
            String key = parameter.split("=", 2)[0];
            String normalized = URLDecoder.decode(key, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if (normalized.equals("user") || normalized.equals("password")) {
                throw new IllegalStateException("Assistant datasource URL must not embed database credentials");
            }
        }
    }
}
