package io.campuscore.restfulapi.site;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The site-appearance KV contract: anonymous browsers read the public homepage
 * chrome, only administrators write it, and whatever an administrator saves is
 * exactly what anonymous readers get back — with a write stamp that makes the
 * save visible to every other browser.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:site_appearance;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class SiteAppearancePersistenceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareTables() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"site\"");
        jdbc.execute("DROP TABLE IF EXISTS \"site\".\"Appearance\"");
        jdbc.execute("""
                CREATE TABLE "site"."Appearance" (
                    "id" VARCHAR(40) PRIMARY KEY,
                    "payload" TEXT NOT NULL,
                    "updatedBy" VARCHAR(255),
                    "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT site_appearance_payload_ck CHECK (length("payload") <= 131072)
                )
                """);
        // Every /api/v1/** request passes AccountStateFilter, which reads the
        // account row from the database when a token is presented.
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("DROP TABLE IF EXISTS \"campuscore_auth\".\"User\"");
        jdbc.execute("""
                CREATE TABLE "campuscore_auth"."User" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "email" VARCHAR(320) NOT NULL,
                    "status" VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
                    "mustChangePassword" BOOLEAN NOT NULL DEFAULT FALSE,
                    "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.update("""
                INSERT INTO "campuscore_auth"."User" ("id", "email") VALUES (?, ?)
                """, "admin-1", "admin-1@campuscore.edu");
    }

    private static JwtRequestPostProcessor admin() {
        return jwt().jwt(token -> token
                        .subject("admin-1")
                        .claim("email", "admin-1@campuscore.edu")
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private MvcResult save(String payload) throws Exception {
        return mvc.perform(put("/api/v1/site-appearance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(admin()))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static JsonNode bodyOf(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("anonymous GET returns an empty object before anything is saved")
    void anonymousGetStartsEmpty() throws Exception {
        mvc.perform(get("/api/v1/site-appearance"))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));
    }

    @Test
    @DisplayName("only administrators save; what they save is what anonymous readers get")
    void adminSavesAndAnonymousReads() throws Exception {
        String payload = """
                {"version":42,"accent":"campus-gold","hero":{"en":{"title":"Welcome"},"vi":{"title":"Chao mung"}},"postOrder":["a1","b2"]}
                """;
        save(payload);

        mvc.perform(get("/api/v1/site-appearance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accent").value("campus-gold"))
                .andExpect(jsonPath("$.hero.en.title").value("Welcome"))
                .andExpect(jsonPath("$.postOrder.length()").value(2))
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        // A second save upserts the single row instead of appending.
        save("{\"version\":43,\"postOrder\":[\"c3\"]}");

        mvc.perform(get("/api/v1/site-appearance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postOrder.length()").value(1));

        var rows = jdbc.queryForList(
                "SELECT \"id\" FROM \"site\".\"Appearance\"", String.class);
        Assertions.assertEquals(List.of(SiteAppearanceStore.ROW_ID), rows);
    }

    @Test
    @DisplayName("every save restamps version and updatedAt, even for an identical payload")
    void writeStampsChangeDetectionFields() throws Exception {
        String payload = "{\"version\":7,\"accent\":\"river-blue\",\"postOrder\":[\"a1\"]}";
        JsonNode first = bodyOf(save(payload));
        JsonNode second = bodyOf(save(payload));

        long firstVersion = first.path("version").asLong();
        long secondVersion = second.path("version").asLong();

        // The caller's own version number is never trusted: a browser that
        // re-sends the payload it already has must still publish a change.
        Assertions.assertTrue(firstVersion > 7, "the write must replace a stale caller version");
        Assertions.assertTrue(
                secondVersion > firstVersion,
                "two saves of the same payload must carry different versions");
        Assertions.assertNotEquals(first.path("updatedAt").asText(), second.path("updatedAt").asText());
        Assertions.assertFalse(
                Instant.parse(second.path("updatedAt").asText()).isBefore(Instant.EPOCH));

        // The stored row is the single source of truth: the stamp a writer is
        // told about is the stamp the next anonymous reader is told about.
        JsonNode readBack = bodyOf(mvc.perform(get("/api/v1/site-appearance"))
                .andExpect(status().isOk())
                .andReturn());
        Assertions.assertEquals(secondVersion, readBack.path("version").asLong());
        Assertions.assertEquals(
                secondVersion,
                JSON.readTree(jdbc.queryForObject(
                        "SELECT \"payload\" FROM \"site\".\"Appearance\" WHERE \"id\" = ?",
                        String.class,
                        SiteAppearanceStore.ROW_ID)).path("version").asLong());
    }

    @Test
    @DisplayName("anonymous PUT is refused while anonymous GET is allowed")
    void anonymousPutIsNotPermitted() throws Exception {
        mvc.perform(get("/api/v1/site-appearance")).andExpect(status().isOk());

        // The filter chain opens only the GET verb, so an anonymous write never
        // reaches the controller: it is an authentication failure, not a
        // permission decision.
        mvc.perform(put("/api/v1/site-appearance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":9}"))
                .andExpect(status().isUnauthorized());

        Assertions.assertEquals(
                0,
                jdbc.queryForObject("SELECT COUNT(*) FROM \"site\".\"Appearance\"", Integer.class));
    }

    @Test
    @DisplayName("non-admin writes are refused")
    void writesStayPrivileged() throws Exception {
        mvc.perform(put("/api/v1/site-appearance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":9}")
                        .with(jwt().jwt(token -> token
                                .subject("student-1")
                                .claim("email", "student-1@campuscore.edu")
                                .claim("roles", List.of("STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a JWT subject longer than the actor column clips instead of failing")
    void longActorSubjectIsClipped() throws Exception {
        String subject = "s".repeat(300);
        mvc.perform(put("/api/v1/site-appearance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"accent\":\"campus-gold\"}")
                        .with(jwt().jwt(token -> token
                                .subject(subject)
                                .claim("email", "long-subject@campuscore.edu")
                                .claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());

        String storedActor = jdbc.queryForObject(
                "SELECT \"updatedBy\" FROM \"site\".\"Appearance\" WHERE \"id\" = ?",
                String.class,
                SiteAppearanceStore.ROW_ID);
        Assertions.assertEquals(SiteAppearanceStore.MAX_UPDATED_BY_CHARS, storedActor.length());
        Assertions.assertTrue(storedActor.length() < subject.length());
    }

    @Test
    @DisplayName("non-object payloads are rejected with 400")
    void nonObjectPayloadRejected() throws Exception {
        mvc.perform(put("/api/v1/site-appearance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1,2,3]")
                        .with(admin()))
                .andExpect(status().isBadRequest());
    }
}
