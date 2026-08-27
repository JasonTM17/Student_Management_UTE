package io.campuscore.restfulapi.thesis.assistant;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({DeepSeekProperties.class, AssistantProperties.class})
public class AssistantConfiguration {
}
