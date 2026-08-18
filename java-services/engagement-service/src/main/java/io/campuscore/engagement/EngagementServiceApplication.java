package io.campuscore.engagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EngagementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EngagementServiceApplication.class, args);
    }
}
