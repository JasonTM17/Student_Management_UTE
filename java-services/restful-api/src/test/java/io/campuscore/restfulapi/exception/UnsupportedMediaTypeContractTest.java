package io.campuscore.restfulapi.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * WS-Security round-11: a JSON body endpoint hit with a non-JSON content type
 * used to fall through to the 500 catch-all (HttpMediaTypeNotSupportedException
 * had no handler). A wrong media type is a client mistake: the contract is a
 * 415 envelope, never a 5xx.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_ws_security_media;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class UnsupportedMediaTypeContractTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("POST /auth/login with text/plain returns 415 with the standard error envelope, not 500")
    void loginWithPlainTextBodyReturns415() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                        .content("email=a@b.c&password=x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
                .andExpect(jsonPath("$.fields").isMap());
    }

    @Test
    @DisplayName("Wrong content type on any JSON endpoint answers 415, never 500 (auth/login form, refresh xml)")
    void otherJsonEndpointsNever500OnWrongMediaType() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                        .content("email=a@b.c&password=x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(org.springframework.http.MediaType.APPLICATION_XML)
                        .content("<request/>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }
}
