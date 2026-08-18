package io.campuscore.thesis.assistant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AssistantConfig {

    @Bean
    RestClient assistantRestClient(AssistantProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        if (!properties.getBaseUrl().isBlank()) {
            builder.baseUrl(properties.getBaseUrl());
        }
        return builder.build();
    }
}
