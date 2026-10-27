package io.campuscore.restfulapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Pins the public JSON defaults across framework upgrades. */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
class JsonDefaultsContractTest {
    @Autowired private ObjectMapper mapper;

    record Payload(String displayName, Instant createdAt, LocalDate date, String optional) { }
    record PrimitivePayload(boolean enabled) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AnnotatedPayload(@JsonFormat(pattern = "dd/MM/yyyy") LocalDate date, String optional) { }

    @Test
    void preservesNamesIsoDatesAndIncludedNulls() throws Exception {
        var payload = new Payload("Nguyễn", Instant.parse("2026-10-27T00:00:00Z"),
                LocalDate.of(2026, 10, 27), null);
        var json = mapper.readTree(mapper.writeValueAsString(payload));
        assertEquals(4, json.size());
        assertEquals("Nguyễn", json.get("displayName").asText());
        assertEquals("2026-10-27T00:00:00Z", json.get("createdAt").asText());
        assertEquals("2026-10-27", json.get("date").asText());
        assertTrue(json.get("optional").isNull());
    }

    @Test
    void ignoresUnknownInputProperties() throws Exception {
        assertEquals(new Payload("A", null, LocalDate.of(2026, 10, 27), null),
                mapper.readValue("{\"displayName\":\"A\",\"date\":\"2026-10-27\",\"unknown\":true}", Payload.class));
    }

    @Test
    void preservesFalseForMissingOrNullPrimitiveProperties() throws Exception {
        assertEquals(new PrimitivePayload(false), mapper.readValue("{}", PrimitivePayload.class));
        assertEquals(new PrimitivePayload(false), mapper.readValue("{\"enabled\":null}", PrimitivePayload.class));
    }

    @Test
    void honorsExistingJacksonAnnotations() throws Exception {
        assertEquals("{\"date\":\"27/10/2026\"}", mapper.writeValueAsString(
                new AnnotatedPayload(LocalDate.of(2026, 10, 27), null)));
    }
}
