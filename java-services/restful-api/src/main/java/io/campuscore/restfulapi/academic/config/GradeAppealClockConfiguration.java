package io.campuscore.restfulapi.academic.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("persistence")
public class GradeAppealClockConfiguration {

    @Bean
    public Clock gradeAppealClock() {
        return Clock.systemUTC();
    }
}
