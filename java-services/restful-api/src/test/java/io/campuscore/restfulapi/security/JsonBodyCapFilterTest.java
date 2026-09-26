package io.campuscore.restfulapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.web.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Audit S5: the JSON body cap is global (1 MB) with tighter route-specific
 * ceilings (4 KB on unauthenticated auth routes, 64 KB on assistant chat), so
 * an oversized body is rejected with 413 before Jackson can materialise it —
 * including on the anonymous POST /auth/login path where unbounded bodies used
 * to be accepted.
 */
class JsonBodyCapFilterTest {

    private JsonBodyCapFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JsonBodyCapFilter(new ApiErrorWriter(new ObjectMapper()));
    }

    private MockHttpServletRequest jsonPost(String uri, int contentLength) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContentType("application/json");
        request.setContent(new byte[contentLength]);
        return request;
    }

    @Test
    void oversizedAnonymousAuthLoginIsRejectedWith413() throws Exception {
        MockHttpServletRequest request = jsonPost("/api/v1/auth/login", 5 * 1024);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("REQUEST_BODY_TOO_LARGE"));
    }

    @Test
    void authBodiesUpToFourKilobytesPass() throws Exception {
        MockHttpServletRequest request = jsonPost("/api/v1/auth/login", 4 * 1024);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void assistantBodyAboveSixtyFourKilobytesIsRejected() throws Exception {
        MockHttpServletRequest request = jsonPost("/api/v1/assistant/chat/stream", 100 * 1024);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(413, response.getStatus());
    }

    @Test
    void businessBodyBetweenAuthAndGlobalCapsPasses() throws Exception {
        // 100 KB would fail the old assistant cap but is fine for a global 1 MB route.
        MockHttpServletRequest request = jsonPost("/api/v1/enrollments/enroll", 100 * 1024);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void bodyAboveGlobalMegabyteCapIsRejectedAnywhere() throws Exception {
        MockHttpServletRequest request = jsonPost("/api/v1/mail/notice", 2 * 1024 * 1024);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(413, response.getStatus());
    }

    @Test
    void nonJsonBodiesAreNeverCapped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setContentType("application/pdf");
        request.setContent(new byte[2 * 1024 * 1024]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void capSelectionFollowsTightestRouteFirst() {
        assertEquals(JsonBodyCapFilter.MAX_JSON_BYTES_AUTH, JsonBodyCapFilter.capFor("/api/v1/auth/login"));
        assertEquals(JsonBodyCapFilter.MAX_JSON_BYTES_AUTH, JsonBodyCapFilter.capFor("/api/v1/auth/change-password"));
        assertEquals(JsonBodyCapFilter.MAX_JSON_BYTES_AUTH, JsonBodyCapFilter.capFor("/api/v1/auth/refresh"));
        assertEquals(JsonBodyCapFilter.MAX_JSON_BYTES_AUTH, JsonBodyCapFilter.capFor("/api/v1/auth/logout"));
        // Profile legitimately carries up to a 200 KB avatar data URL, so it
        // keeps the global cap and its 400 bean-validation contract.
        assertEquals(JsonBodyCapFilter.MAX_JSON_BYTES_GLOBAL, JsonBodyCapFilter.capFor("/api/v1/auth/profile"));
        assertEquals(JsonBodyCapFilter.MAX_JSON_BYTES_ASSISTANT, JsonBodyCapFilter.capFor("/api/v1/assistant/chat"));
        assertEquals(JsonBodyCapFilter.MAX_JSON_BYTES_ASSISTANT,
                JsonBodyCapFilter.capFor("/api/v1/thesis/assistant/chat"));
        assertEquals(JsonBodyCapFilter.MAX_JSON_BYTES_GLOBAL, JsonBodyCapFilter.capFor("/api/v1/enrollments"));
        assertEquals(JsonBodyCapFilter.MAX_JSON_BYTES_GLOBAL, JsonBodyCapFilter.capFor(null));
    }
}
