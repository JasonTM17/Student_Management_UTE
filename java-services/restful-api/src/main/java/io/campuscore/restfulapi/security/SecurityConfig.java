package io.campuscore.restfulapi.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.campuscore.restfulapi.web.ApiErrorWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    JwtDecoder jwtDecoder(@Value("${security.jwt.secret}") String secret) {
        SecretKeySpec key = jwtSecretKey(secret);
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${security.jwt.secret}") String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey(secret)));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static SecretKeySpec jwtSecretKey(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 characters");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CookieOrBearerTokenResolver tokenResolver,
            CsrfCookieFilter csrfCookieFilter,
            ApiErrorWriter errorWriter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/email-verifications/**",
                                "/api/v1/auth/password-reset-requests",
                                "/api/v1/auth/password-reset/confirm",
                                "/api/v1/auth/verify-email",
                                "/api/v1/auth/resend-verification",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/contract",
                                "/api/v1/health/**",
                                "/internal/rag/**",
                                "/error",
                                "/actuator/health/**",
                                "/api/docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint(errorWriter))
                        .accessDeniedHandler(accessDeniedHandler(errorWriter)))
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(tokenResolver)
                        .authenticationEntryPoint(authenticationEntryPoint(errorWriter))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .addFilterAfter(csrfCookieFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    private AuthenticationEntryPoint authenticationEntryPoint(ApiErrorWriter errorWriter) {
        return (request, response, exception) -> errorWriter.write(
                request,
                response,
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                "Authentication is required");
    }

    private AccessDeniedHandler accessDeniedHandler(ApiErrorWriter errorWriter) {
        return (request, response, exception) -> errorWriter.write(
                request,
                response,
                org.springframework.http.HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Access denied");
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::authoritiesFromClaims);
        return converter;
    }

    static Collection<GrantedAuthority> authoritiesFromClaims(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        addAuthorities(authorities, jwt.getClaims().get("roles"), "ROLE_");
        addAuthorities(authorities, jwt.getClaims().get("permissions"), "PERM_");
        return authorities;
    }

    private static void addAuthorities(List<GrantedAuthority> authorities, Object claim, String prefix) {
        if (claim == null) {
            return;
        }
        if (!(claim instanceof Collection<?> values)) {
            throw new BadCredentialsException("Invalid authority claim");
        }
        for (Object value : values) {
            if (!(value instanceof String text) || text.isBlank()) {
                throw new BadCredentialsException("Invalid authority claim");
            }
            authorities.add(new SimpleGrantedAuthority(prefix + text));
        }
    }
}
