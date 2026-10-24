package io.campuscore.restfulapi.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.security.JsonBodyCapFilter;
import io.campuscore.restfulapi.security.RequestIdFilter;
import io.campuscore.restfulapi.exception.response.ApiErrorWriter;
import io.campuscore.restfulapi.exception.code.ErrorCode;
import io.campuscore.restfulapi.exception.handler.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Error-envelope characterization test. Exercises every exception type handled
 * by {@link GlobalExceptionHandler} plus the filter-level {@link ApiErrorWriter}
 * body and pins the exact status, body keys, and stable values. Bodies are
 * additionally dumped to {@code -Derrorcontract.dump=<dir>} when the property
 * is set so refactors can byte-compare runs.
 */
class ErrorEnvelopeContractTest {

    private static final String REQUEST_ID = "req-envelope-probe";
    private static final List<String> HANDLER_KEYS =
            List.of("timestamp", "status", "code", "message", "path", "requestId", "fieldErrors", "fields");
    private static final List<String> HANDLER_KEYS_WITH_DETAILS =
            List.of("timestamp", "status", "code", "message", "path", "requestId", "fieldErrors", "fields", "details");
    private static final List<String> WRITER_KEYS =
            List.of("status", "code", "message", "path", "requestId", "timestamp", "fields");

    private final ObjectMapper mapper = new ObjectMapper();
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private record ProbeBody(@NotBlank String name) { }

    @RestController
    static class ProbeController {
        @GetMapping("/probe/app-exception")
        Object appException() {
            throw new AppException(ErrorCode.SECTION_FULL);
        }

        @GetMapping("/probe/app-exception-custom")
        Object appExceptionCustom() {
            throw new AppException(ErrorCode.SECTION_FULL, "Section S1 is capped at 40 seats");
        }

        @GetMapping("/probe/domain")
        Object domain() {
            throw new DomainException(HttpStatus.CONFLICT, "SECTION_REOPEN_CONFLICT", "Reopen conflicts with active registrations");
        }

        @GetMapping("/probe/domain-details")
        Object domainDetails() {
            throw new DomainException(HttpStatus.CONFLICT, "SECTION_REOPEN_CONFLICT",
                    "Reopen conflicts with active registrations", Map.of("sectionIds", List.of("s1", "s2")));
        }

        @GetMapping("/probe/bad-request")
        Object badRequest() {
            throw new BadRequestException("semester code is malformed");
        }

        @GetMapping("/probe/conflict")
        Object conflict() {
            throw new ConflictException("student already enrolled");
        }

        @GetMapping("/probe/forbidden")
        Object forbidden() {
            throw new ForbiddenException("lecturer cannot publish grades");
        }

        @GetMapping("/probe/not-found")
        Object notFound() {
            throw new ResourceNotFoundException("Section", "id", "sec-9");
        }

        @GetMapping("/probe/unauthorized")
        Object unauthorized() {
            throw new UnauthorizedException("login required for this endpoint");
        }

        @GetMapping("/probe/validation")
        Object validation() {
            throw new ValidationException("Profile update failed",
                    Map.of("email", "must be a university address", "displayName", "must not be blank"));
        }

        @PostMapping(value = "/probe/echo", consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
        Object echo(@RequestBody Map<String, Object> body) {
            return body;
        }

        @PostMapping("/probe/validated")
        Object validated(@Valid @RequestBody ProbeBody body) {
            return body;
        }

        @GetMapping("/probe/need-param")
        Object needParam(@RequestParam String q) {
            return q;
        }

        @GetMapping("/probe/no-resource")
        Object noResource() throws org.springframework.web.servlet.resource.NoResourceFoundException {
            throw new org.springframework.web.servlet.resource.NoResourceFoundException(
                    org.springframework.http.HttpMethod.GET, "/probe/no-resource");
        }

        @GetMapping("/probe/int-param")
        Object intParam(@RequestParam int n) {
            return n;
        }

        @GetMapping("/probe/access-denied")
        Object accessDenied() {
            throw new AccessDeniedException("denied by policy");
        }

        @GetMapping("/probe/authentication")
        Object authentication() {
            throw new BadCredentialsException("bad token");
        }

        @GetMapping("/probe/response-status-404")
        Object responseStatus404() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        @GetMapping("/probe/response-status-422")
        Object responseStatus422() {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "cannot process roster");
        }

        @GetMapping("/probe/data-integrity")
        Object dataIntegrity() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        }

        @GetMapping("/probe/upload-size")
        Object uploadSize() {
            throw new MaxUploadSizeExceededException(21L * 1024 * 1024);
        }

        @GetMapping("/probe/illegal-argument")
        Object illegalArgument() {
            throw new IllegalArgumentException("round id must be positive");
        }

        @GetMapping("/probe/boom")
        Object boom() {
            throw new IllegalStateException("unexpected sink state");
        }
    }

    private RequestPostProcessor requestId() {
        return request -> {
            request.setAttribute(RequestIdFilter.ATTRIBUTE, REQUEST_ID);
            return request;
        };
    }

    private Map<String, Object> perform(String name, org.springframework.test.web.servlet.RequestBuilder request)
            throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        String raw = result.getResponse().getContentAsString();
        dump(name, raw);
        Map<String, Object> body = mapper.readValue(raw, new TypeReference<>() { });
        return body;
    }

    private Map<String, Object> expectEnvelope(String name,
            org.springframework.test.web.servlet.RequestBuilder request,
            int status, String code, String message, String path) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        String raw = result.getResponse().getContentAsString();
        dump(name, raw);
        assertEquals(status, result.getResponse().getStatus());
        Map<String, Object> body = mapper.readValue(raw, new TypeReference<>() { });
        assertEquals(new LinkedHashSet<>(HANDLER_KEYS), new LinkedHashSet<>(body.keySet()),
                () -> "unexpected envelope keys for " + name + ": " + body.keySet());
        assertEquals(status, body.get("status"));
        assertEquals(code, body.get("code"));
        assertEquals(message, body.get("message"));
        assertEquals(path, body.get("path"));
        assertEquals(REQUEST_ID, body.get("requestId"));
        assertTrue(body.get("timestamp") instanceof String ts && ts.endsWith("Z"),
                () -> "timestamp shape changed: " + body.get("timestamp"));
        assertEquals(List.of(), body.get("fieldErrors"));
        assertEquals(Map.of(), body.get("fields"));
        return body;
    }

    private Map<String, Object> expectEnvelopeWithDetails(String name,
            org.springframework.test.web.servlet.RequestBuilder request,
            int status, String code, String message, String path) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        String raw = result.getResponse().getContentAsString();
        dump(name, raw);
        assertEquals(status, result.getResponse().getStatus());
        Map<String, Object> body = mapper.readValue(raw, new TypeReference<>() { });
        assertEquals(new LinkedHashSet<>(HANDLER_KEYS_WITH_DETAILS), new LinkedHashSet<>(body.keySet()));
        assertEquals(status, body.get("status"));
        assertEquals(code, body.get("code"));
        assertEquals(message, body.get("message"));
        assertEquals(path, body.get("path"));
        return body;
    }

    private void dump(String name, String raw) {
        String dir = System.getProperty("errorcontract.dump");
        if (dir == null || dir.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(Path.of(dir));
            String normalized = raw.replaceAll("\"timestamp\":\"[^\"]*\"", "\"timestamp\":\"<ts>\"");
            Files.writeString(Path.of(dir, name + ".json"), normalized);
        } catch (IOException exception) {
            throw new IllegalStateException("could not dump contract body " + name, exception);
        }
    }

    @Test
    void appExceptionByErrorCode() throws Exception {
        expectEnvelope("app-exception", get("/probe/app-exception").with(requestId()),
                409, "SECTION_FULL", "Class section has reached maximum capacity", "/probe/app-exception");
    }

    @Test
    void appExceptionWithCustomMessage() throws Exception {
        expectEnvelope("app-exception-custom", get("/probe/app-exception-custom").with(requestId()),
                409, "SECTION_FULL", "Section S1 is capped at 40 seats", "/probe/app-exception-custom");
    }

    @Test
    void domainExceptionWithoutDetails() throws Exception {
        Map<String, Object> body = perform("domain",
                get("/probe/domain").with(requestId()));
        assertEquals(409, body.get("status"));
        assertEquals("SECTION_REOPEN_CONFLICT", body.get("code"));
        assertEquals("Reopen conflicts with active registrations", body.get("message"));
        assertEquals(new LinkedHashSet<>(HANDLER_KEYS), new LinkedHashSet<>(body.keySet()));
        assertNull(body.get("details"));
    }

    @Test
    void domainExceptionWithDetails() throws Exception {
        Map<String, Object> body = expectEnvelopeWithDetails("domain-details",
                get("/probe/domain-details").with(requestId()),
                409, "SECTION_REOPEN_CONFLICT", "Reopen conflicts with active registrations",
                "/probe/domain-details");
        assertEquals(Map.of("sectionIds", List.of("s1", "s2")), body.get("details"));
    }

    @Test
    void typedSubclasses() throws Exception {
        expectEnvelope("bad-request", get("/probe/bad-request").with(requestId()),
                400, "BAD_REQUEST", "semester code is malformed", "/probe/bad-request");
        expectEnvelope("conflict", get("/probe/conflict").with(requestId()),
                409, "CONFLICT", "student already enrolled", "/probe/conflict");
        expectEnvelope("forbidden", get("/probe/forbidden").with(requestId()),
                403, "FORBIDDEN", "lecturer cannot publish grades", "/probe/forbidden");
        expectEnvelope("not-found", get("/probe/not-found").with(requestId()),
                404, "RESOURCE_NOT_FOUND", "Section not found with id: 'sec-9'", "/probe/not-found");
        expectEnvelope("unauthorized", get("/probe/unauthorized").with(requestId()),
                401, "UNAUTHENTICATED", "login required for this endpoint", "/probe/unauthorized");
    }

    @Test
    void validationExceptionCarriesFieldErrors() throws Exception {
        MvcResult result = mvc.perform(get("/probe/validation").with(requestId())).andReturn();
        dump("validation", result.getResponse().getContentAsString());
        Map<String, Object> body = mapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<>() { });
        assertEquals(400, body.get("status"));
        assertEquals("VALIDATION_ERROR", body.get("code"));
        assertEquals("Profile update failed", body.get("message"));
        assertEquals(Map.of("email", "must be a university address", "displayName", "must not be blank"),
                body.get("fields"));
        assertEquals(Set.of(
                Map.of("field", "email", "message", "must be a university address"),
                Map.of("field", "displayName", "message", "must not be blank")),
                Set.copyOf((List<Map<String, Object>>) body.get("fieldErrors")));
    }

    @Test
    void beanValidationFailure() throws Exception {
        MvcResult result = mvc.perform(post("/probe/validated").with(requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        dump("bean-validation", result.getResponse().getContentAsString());
        Map<String, Object> body = mapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<>() { });
        assertEquals(400, body.get("status"));
        assertEquals("VALIDATION_ERROR", body.get("code"));
        assertEquals("Request validation failed", body.get("message"));
        assertEquals(Map.of("name", "must not be blank"), body.get("fields"));
        assertEquals(List.of(Map.of("field", "name", "message", "must not be blank")),
                body.get("fieldErrors"));
    }

    @Test
    void malformedJsonBody() throws Exception {
        expectEnvelope("malformed-json", post("/probe/echo").with(requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"),
                400, "INVALID_REQUEST", "Request could not be parsed", "/probe/echo");
    }

    @Test
    void missingRequestParameter() throws Exception {
        expectEnvelope("missing-param", get("/probe/need-param").with(requestId()),
                400, "INVALID_REQUEST", "Request could not be parsed", "/probe/need-param");
    }

    @Test
    void requestParameterTypeMismatch() throws Exception {
        expectEnvelope("type-mismatch", get("/probe/int-param").param("n", "abc").with(requestId()),
                400, "INVALID_REQUEST", "Request could not be parsed", "/probe/int-param");
    }

    @Test
    void noResourceFound() throws Exception {
        expectEnvelope("no-resource", get("/probe/no-resource").with(requestId()),
                404, "NOT_FOUND", "Resource not found", "/probe/no-resource");
    }

    @Test
    void wrongMethodOnMappedPath() throws Exception {
        expectEnvelope("method-not-allowed", put("/probe/echo").with(requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"),
                405, "METHOD_NOT_ALLOWED", "Request method is not supported for this resource", "/probe/echo");
    }

    @Test
    void unsupportedContentType() throws Exception {
        expectEnvelope("unsupported-media", post("/probe/echo").with(requestId())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain body"),
                415, "UNSUPPORTED_MEDIA_TYPE", "Request content type is not supported", "/probe/echo");
    }

    @Test
    void unacceptableAcceptHeader() throws Exception {
        // A client that only accepts a media type the endpoint cannot produce
        // gets a bare 406: the JSON envelope itself cannot be rendered for a
        // non-JSON Accept, so the body stays empty.
        MvcResult result = mvc.perform(post("/probe/echo").with(requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andReturn();
        dump("not-acceptable", result.getResponse().getContentAsString());
        assertEquals(406, result.getResponse().getStatus());
        assertEquals("", result.getResponse().getContentAsString());
    }

    @Test
    void accessDenied() throws Exception {
        expectEnvelope("access-denied", get("/probe/access-denied").with(requestId()),
                403, "ACCESS_DENIED", "Access denied", "/probe/access-denied");
    }

    @Test
    void authenticationRequired() throws Exception {
        expectEnvelope("authentication", get("/probe/authentication").with(requestId()),
                401, "UNAUTHENTICATED", "Authentication is required", "/probe/authentication");
    }

    @Test
    void responseStatusNotFoundUsesSemanticCode() throws Exception {
        expectEnvelope("response-status-404", get("/probe/response-status-404").with(requestId()),
                404, "NOT_FOUND", "Not Found", "/probe/response-status-404");
    }

    @Test
    void responseStatusOtherUsesHttpCode() throws Exception {
        expectEnvelope("response-status-422", get("/probe/response-status-422").with(requestId()),
                422, "HTTP_422", "cannot process roster", "/probe/response-status-422");
    }

    @Test
    void dataIntegrityViolation() throws Exception {
        expectEnvelope("data-integrity", get("/probe/data-integrity").with(requestId()),
                409, "CONFLICT", "Operation conflicted with existing database state", "/probe/data-integrity");
    }

    @Test
    void maxUploadSizeExceeded() throws Exception {
        expectEnvelope("upload-size", get("/probe/upload-size").with(requestId()),
                413, "FILE_TOO_LARGE", "The uploaded document must be 20 MB or smaller", "/probe/upload-size");
    }

    @Test
    void illegalArgument() throws Exception {
        expectEnvelope("illegal-argument", get("/probe/illegal-argument").with(requestId()),
                400, "INVALID_REQUEST", "round id must be positive", "/probe/illegal-argument");
    }

    @Test
    void unhandledExceptionIsGenericInternalError() throws Exception {
        expectEnvelope("boom", get("/probe/boom").with(requestId()),
                500, "INTERNAL_ERROR", "An unexpected internal error occurred", "/probe/boom");
    }

    @Test
    void filterWrittenBodySharesEnvelopeShape() throws Exception {
        ApiErrorWriter writer = new ApiErrorWriter(mapper);
        JsonBodyCapFilter filter = new JsonBodyCapFilter(writer);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setContentType("application/json");
        request.setContent(new byte[5 * 1024]);
        request.setAttribute(RequestIdFilter.ATTRIBUTE, REQUEST_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(413, response.getStatus());
        String raw = response.getContentAsString();
        dump("filter-body-cap", raw);
        Map<String, Object> body = mapper.readValue(raw, new TypeReference<>() { });
        assertEquals(new LinkedHashSet<>(WRITER_KEYS), new LinkedHashSet<>(body.keySet()),
                () -> "writer envelope keys changed: " + body.keySet());
        assertEquals(List.copyOf(WRITER_KEYS), List.copyOf(body.keySet()),
                () -> "writer envelope key order changed: " + body.keySet());
        assertEquals(413, body.get("status"));
        assertEquals("REQUEST_BODY_TOO_LARGE", body.get("code"));
        assertEquals("/api/v1/auth/login", body.get("path"));
        assertEquals(REQUEST_ID, body.get("requestId"));
        assertEquals(Map.of(), body.get("fields"));
    }
}
