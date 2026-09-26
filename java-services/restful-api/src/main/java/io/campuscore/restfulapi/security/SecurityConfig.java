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
import org.springframework.http.HttpMethod;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.reject-known-defaults:false}") boolean rejectKnownDefaults) {
        SecretKeySpec key = jwtSecretKey("JWT_SECRET", secret, rejectKnownDefaults);
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        return token -> requireAccessTokenShape(decoder.decode(token));
    }

    /**
     * Refuse any bearer token that was not issued as an access token. Refresh
     * tokens carry {@code tokenType=refresh} and no authority claims, so without
     * this check a refresh token presented as {@code Authorization: Bearer}
     * authenticates as a claim-less principal wherever the route only asks for
     * {@code isAuthenticated()}. The {@code roles} claim is likewise required:
     * every access token this service mints carries it.
     */
    static Jwt requireAccessTokenShape(Jwt jwt) {
        if (!"access".equals(jwt.getClaimAsString("tokenType"))) {
            throw new BadCredentialsException("Invalid access token");
        }
        if (jwt.getClaims().get("roles") == null) {
            throw new BadCredentialsException("Invalid authority claim");
        }
        return jwt;
    }

    /**
     * Startup guard against the misconfiguration where both JWT secrets resolve
     * to the same value (the refresh-secret property deliberately falls back to
     * the access secret when unset). With identical secrets the tokenType checks
     * are the only thing separating the two token families, so production asks
     * for two independently generated secrets and fails closed here.
     */
    @Bean
    Object jwtSecretPairPolicy(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.refresh-secret:${security.jwt.secret}}") String refreshSecret,
            @Value("${security.jwt.reject-known-defaults:false}") boolean rejectKnownDefaults) {
        JwtSecretPolicy.enforceDistinct("JWT_SECRET", secret, "JWT_REFRESH_SECRET", refreshSecret, rejectKnownDefaults);
        return new Object();
    }

    @Bean
    JwtEncoder jwtEncoder(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.reject-known-defaults:false}") boolean rejectKnownDefaults) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey("JWT_SECRET", secret, rejectKnownDefaults)));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static SecretKeySpec jwtSecretKey(String variable, String secret, boolean rejectKnownDefaults) {
        JwtSecretPolicy.enforce(variable, secret, rejectKnownDefaults);
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * Credentialed CORS is allowlist-only. The default list carries local dev
     * hosts and the production site; extra origins (e.g. a preview deployment)
     * must be enumerated explicitly via APP_CORS_ALLOWED_ORIGIN_PATTERNS —
     * no public wildcard such as *.vercel.app.
     */
    static final String DEFAULT_CORS_ORIGIN_PATTERNS =
            "http://localhost:[*],http://127.0.0.1:[*],http://localhost:3000,http://127.0.0.1:3000,"
                    + "http://127.0.0.1:3100,https://campusute.io.vn,https://www.campusute.io.vn";

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin-patterns:" + DEFAULT_CORS_ORIGIN_PATTERNS + "}") String allowedOriginPatterns) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(allowedOriginPatterns.split("\\s*,\\s*")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of(
                "Authorization",
                "Content-Disposition",
                "Deprecation",
                "Sunset",
                "Idempotency-Key",
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "X-RateLimit-Reset",
                "Retry-After"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CookieOrBearerTokenResolver tokenResolver,
            CsrfCookieFilter csrfCookieFilter,
            io.campuscore.restfulapi.security.ratelimit.RateLimitFilter rateLimitFilter,
            AccountStateFilter accountStateFilter,
            ApiErrorWriter errorWriter,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                // Second step of a two-factor login: the
                                // caller holds only the emailed challenge id,
                                // no token yet, so it must stay anonymous.
                                // IP rate limiting lives in RateLimitFilter.
                                "/api/v1/auth/two-factor/verify",
                                "/api/v1/contract",
                                "/api/v1/health/**",
                                // Anonymous public campus news feed for the
                                // homepage; read-only and safe-subset only.
                                "/api/v1/announcements/public",
                                // Read-only public-safe category feed the
                                // anonymous homepage uses to label articles by
                                // their editorial taxonomy instead of guessing.
                                "/api/v1/article-taxonomy/v2/categories",
                                "/internal/rag/assistant/**",
                                "/internal/rag/thesis/assistant/**",
                                // Mail endpoints used to be reachable without a
                                // token; sending mail is now a staff action and
                                // the template previews require any login.
                                "/error",
                                "/actuator/health/**",
                                "/api/docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**")
                        .permitAll()
                        // Public site chrome (accent, homepage hero, post
                        // order). Anonymous access is pinned to the read verb:
                        // a PUT to the same path matches no permitAll rule, so
                        // it falls through to authentication and then to the
                        // controller's administrator-only @PreAuthorize.
                        .requestMatchers(HttpMethod.GET, "/api/v1/site-appearance")
                        .permitAll()
                        // Admin-only mutation surfaces deny wrong roles at the
                        // URL level, so a request that also fails bean
                        // validation is answered 403 ACCESS_DENIED instead of
                        // leaking the validation contract (400) to a caller
                        // who was never allowed to write here. The controllers'
                        // @PreAuthorize guards stay as defense in depth.
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/**", "/api/v1/admin/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/**", "/api/v1/admin/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/**", "/api/v1/admin/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/**", "/api/v1/admin/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint(errorWriter))
                        .accessDeniedHandler(accessDeniedHandler(errorWriter)))
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(tokenResolver)
                        .authenticationEntryPoint(authenticationEntryPoint(errorWriter))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .addFilterAfter(csrfCookieFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(accountStateFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, CsrfCookieFilter.class);

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
