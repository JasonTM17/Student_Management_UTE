package io.campuscore.restfulapi.site;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import java.util.List;

/**
 * The site-appearance KV contract: anonymous browsers read the public homepage
 * chrome, only administrators write it, and whatever an administrator saves is
 * exactly what anonymous readers get back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:site_appearance;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class SiteAppearancePersistenceTest {

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
                    "updatedBy" VARCHAR(120),
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
                {"version":42,"accent":"campus-gold","hero":{"en":{"title":"Welcome"},"vi":{"title":"Chào mừng"}},"postOrder":["a1","b2"]}
                """;
        mvc.perform(put("/api/v1/site-appearance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(admin()))
                .andExpect(status().isOk());

        // A second save upserts the single row instead of appending.
        mvc.perform(put("/api/v1/site-appearance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":43,\"postOrder\":[\"c3\"]}")
                        .with(admin()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/site-appearance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(43))
                .andExpect(jsonPath("$.postOrder.length()").value(1));

        var rows = jdbc.queryForList(
                "SELECT \"id\" FROM \"site\".\"Appearance\"", String.class);
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(SiteAppearanceStore.ROW_ID), rows);
    }

    @Test
    @DisplayName("anonymous and non-admin writes are refused")
    void writesStayPrivileged() throws Exception {
        mvc.perform(put("/api/v1/site-appearance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":9}"))
                .andExpect(status().isForbidden());

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
    @DisplayName("non-object payloads are rejected with 400")
    void nonObjectPayloadRejected() throws Exception {
        mvc.perform(put("/api/v1/site-appearance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1,2,3]")
                        .with(admin()))
                .andExpect(status().isBadRequest());
    }
}
