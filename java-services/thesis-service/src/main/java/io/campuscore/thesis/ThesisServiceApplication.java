package io.campuscore.thesis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ThesisServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThesisServiceApplication.class, args);
    }
}
