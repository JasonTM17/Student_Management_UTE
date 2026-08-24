package io.campuscore.restfulapi.registration;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationConfiguration {
    @Bean
    Clock registrationClock() { return Clock.systemUTC(); }
}
