package io.campuscore.restfulapi.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * WS-Security round-11 D3: admin-only mutation surfaces (users, admin/*) deny
 * wrong roles at the URL level, so a request whose body also fails bean
 * validation is answered 403 ACCESS_DENIED — the validation contract (field
 * names, requiredness) is not disclosed to callers who were never allowed to
 * write here. Live evidence on the pre-fix build: a student POST /users with
 * an invalid body returned 400 VALIDATION_ERROR before this rule existed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_ws_security_deny_order;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class AdminMutationDenyOrderingTest {

    private static final String INVALID_USER_BODY = "{\"email\":\"not-an-email\"}";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("Student POST /users with an invalid body is 403 ACCESS_DENIED, not 400 VALIDATION_ERROR")
    void studentInvalidBodyOnUserCreateIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .with(studentJwt())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(INVALID_USER_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Anonymous POST /users still answers 401 UNAUTHENTICATED before the role rule")
    void anonymousUserCreateStaysUnauthenticated() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(INVALID_USER_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Admin still reaches bean validation on POST /users (400 VALIDATION_ERROR, not 403)")
    void adminInvalidBodyStillReachesValidation() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .with(adminJwt())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(INVALID_USER_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private RequestPostProcessor studentJwt() {
        return jwt().jwt(token -> token
                        .subject("deny-order-student")
                        .claim("roles", List.of("STUDENT")))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                        .subject("deny-order-admin")
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
