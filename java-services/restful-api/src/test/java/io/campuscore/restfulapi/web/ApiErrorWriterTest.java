package io.campuscore.restfulapi.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiErrorWriterTest {

    @Test
    void writesStandardUniformErrorPayloadWithStatusInteger() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ApiErrorWriter writer = new ApiErrorWriter(mapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/resource");
        request.setAttribute(io.campuscore.restfulapi.security.RequestIdFilter.ATTRIBUTE, "req-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));

        Map<String, Object> body = mapper.readValue(response.getContentAsString(), new TypeReference<>() {});
        assertEquals(401, body.get("status"));
        assertEquals("UNAUTHORIZED", body.get("code"));
        assertEquals("Authentication required", body.get("message"));
        assertEquals("/api/v1/test/resource", body.get("path"));
        assertEquals("req-12345", body.get("requestId"));
        assertNotNull(body.get("timestamp"));
        assertTrue(body.containsKey("fields"));
    }
}
