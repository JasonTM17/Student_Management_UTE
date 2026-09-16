package io.campuscore.restfulapi.thesis.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Selects local preview storage or the explicitly configured Supabase adapter. */
@Configuration
@EnableConfigurationProperties(ThesisReportStorageProperties.class)
public class ThesisReportStorageConfiguration {

    @Bean
    public ThesisReportStorage thesisReportStorage(ThesisReportStorageProperties properties) {
        String provider = properties.getProvider() == null
                ? "local"
                : properties.getProvider().trim().toLowerCase(java.util.Locale.ROOT);
        return switch (provider) {
            case "local" -> new LocalThesisReportStorage(properties);
            case "supabase" -> new SupabaseThesisReportStorage(properties);
            default -> throw new IllegalStateException(
                    "Unsupported thesis.report.storage.provider: " + provider);
        };
    }
}
