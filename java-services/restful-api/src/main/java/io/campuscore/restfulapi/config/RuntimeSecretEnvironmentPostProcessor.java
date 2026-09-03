package io.campuscore.restfulapi.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Resolves production secrets before Spring binds datasource, security, mail,
 * provider, and assistant properties. When a *_FILE variable is present the
 * file is authoritative and an unreadable or blank value aborts startup.
 */
public final class RuntimeSecretEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    static final String PROPERTY_SOURCE = "campuscoreRuntimeSecrets";

    private static final Map<String, String> SECRET_FILES = Map.ofEntries(
            Map.entry("SPRING_DATASOURCE_PASSWORD_FILE", "spring.datasource.password"),
            Map.entry("JWT_SECRET_FILE", "security.jwt.secret"),
            Map.entry("JWT_REFRESH_SECRET_FILE", "security.jwt.refresh-secret"),
            Map.entry("HEALTH_READINESS_KEY_FILE", "health.readiness-key"),
            Map.entry("ASSISTANT_RAG_SERVICE_TOKEN_FILE", "assistant.rag.service-token"),
            Map.entry("SUPABASE_SERVICE_ROLE_KEY_FILE", "assistant.supabase.service-role-key"),
            Map.entry("DEEPSEEK_API_KEY_FILE", "deepseek.api-key"),
            Map.entry("SPRING_MAIL_PASSWORD_FILE", "spring.mail.password"));

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> mapping : SECRET_FILES.entrySet()) {
            boolean optional = isOptionalProviderSecret(mapping.getKey());
            boolean enabled = optional
                    && Boolean.parseBoolean(environment.getProperty(optionalFlag(mapping.getKey()), "false"));
            if (optional && !enabled) {
                // Provider integrations are disabled by default.  An absent
                // optional file must not make a lexical-only stack fail, while
                // enabling the integration still requires the file below.
                continue;
            }
            String file = environment.getProperty(mapping.getKey());
            if (file == null || file.isBlank()) {
                if (enabled) {
                    String direct = environment.getProperty(mapping.getValue());
                    if (direct != null && !direct.isBlank()) {
                        continue;
                    }
                    throw new IllegalStateException(mapping.getKey() + " is required when its integration is enabled");
                }
                continue;
            }
            resolved.put(mapping.getValue(), readRequiredSecret(mapping.getKey(), file));
        }
        if (!resolved.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, resolved));
        }
    }

    private static boolean isOptionalProviderSecret(String variable) {
        return "DEEPSEEK_API_KEY_FILE".equals(variable)
                || "SUPABASE_SERVICE_ROLE_KEY_FILE".equals(variable)
                || "SPRING_MAIL_PASSWORD_FILE".equals(variable);
    }

    private static String optionalFlag(String variable) {
        return switch (variable) {
            case "DEEPSEEK_API_KEY_FILE" -> "deepseek.enabled";
            case "SUPABASE_SERVICE_ROLE_KEY_FILE" -> "assistant.supabase.enabled";
            case "SPRING_MAIL_PASSWORD_FILE" -> "mail.enabled";
            default -> "";
        };
    }

    static String readRequiredSecret(String variable, String file) {
        Path path;
        try {
            path = Path.of(file).toAbsolutePath().normalize();
        } catch (RuntimeException invalidPath) {
            throw new IllegalStateException(variable + " does not reference a valid secret file", invalidPath);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(variable + " secret file is missing or is not a regular file");
        }
        try {
            String value = Files.readString(path, StandardCharsets.UTF_8).strip();
            if (value.isBlank()) throw new IllegalStateException(variable + " secret file is blank");
            return value;
        } catch (IOException exception) {
            throw new IllegalStateException(variable + " secret file cannot be read", exception);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
