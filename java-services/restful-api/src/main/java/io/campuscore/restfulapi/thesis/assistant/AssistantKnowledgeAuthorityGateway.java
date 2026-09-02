package io.campuscore.restfulapi.thesis.assistant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantKnowledgeAdminController.KnowledgeDocumentView;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantKnowledgeAdminController.KnowledgeRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantKnowledgeAdminController.KnowledgeRevision;
import io.campuscore.restfulapi.web.ApiExceptionHandler.ApiError;
import io.campuscore.restfulapi.web.DomainException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * REST-edge gateway for the production knowledge authority.
 *
 * The edge authenticates a CampusCore administrator, then forwards only the
 * actor subject and the validated payload over the private RAG network.  It
 * never receives a Supabase URL or service-role key.
 */
@Component
@Profile("persistence")
@ConditionalOnProperty(prefix = "assistant.knowledge", name = "authority-mode", havingValue = "remote")
public class AssistantKnowledgeAuthorityGateway {
    private static final String TOKEN_HEADER = "X-Rag-Service-Token";
    private static final String OWNER_HEADER = "X-Assistant-Owner";

    private final AssistantRagProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public AssistantKnowledgeAuthorityGateway(AssistantRagProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .build());
    }

    AssistantKnowledgeAuthorityGateway(AssistantRagProperties properties, ObjectMapper mapper, HttpClient http) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = http;
    }

    public List<KnowledgeDocumentView> list(String actor, String domain, String state) {
        return exchange("GET", "/knowledge", query(domain, state), null, actor,
                new TypeReference<List<KnowledgeDocumentView>>() { });
    }

    public KnowledgeDocumentView get(UUID id, String actor) {
        return exchange("GET", "/knowledge/" + id, Map.of(), null, actor,
                new TypeReference<KnowledgeDocumentView>() { });
    }

    public KnowledgeRevision create(KnowledgeRequest request, String actor) {
        return exchange("POST", "/knowledge", Map.of(), request, actor,
                new TypeReference<KnowledgeRevision>() { });
    }

    public KnowledgeRevision update(UUID id, KnowledgeRequest request, String actor) {
        return exchange("PUT", "/knowledge/" + id, Map.of(), request, actor,
                new TypeReference<KnowledgeRevision>() { });
    }

    public KnowledgeRevision submit(UUID id, String actor) {
        return exchange("POST", "/knowledge/" + id + "/submit", Map.of(), null, actor,
                new TypeReference<KnowledgeRevision>() { });
    }

    public KnowledgeRevision publish(UUID id, String actor) {
        return exchange("POST", "/knowledge/" + id + "/publish", Map.of(), null, actor,
                new TypeReference<KnowledgeRevision>() { });
    }

    public void archive(UUID id, String actor) {
        exchange("DELETE", "/knowledge/" + id, Map.of(), null, actor, null);
    }

    public SupabaseKnowledgeSyncService.SyncResult sync(String actor) {
        return exchange("POST", "/knowledge/sync", Map.of(), null, actor,
                new TypeReference<SupabaseKnowledgeSyncService.SyncResult>() { });
    }

    public SupabaseKnowledgeSyncService.SyncResult status(String actor) {
        return exchange("GET", "/knowledge/sync-status", Map.of(), null, actor,
                new TypeReference<SupabaseKnowledgeSyncService.SyncResult>() { });
    }

    private <T> T exchange(String method, String path, Map<String, String> query,
            Object body, String actor, TypeReference<T> type) {
        ensureReady(actor);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path, query))
                .timeout(Duration.ofMillis(properties.readTimeoutMs()))
                .header("Accept", "application/json")
                .header(TOKEN_HEADER, properties.serviceToken())
                .header(OWNER_HEADER, actor);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, bodyPublisher(body));
        }
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw problem(response.statusCode(), response.body());
            }
            if (type == null || response.body() == null || response.body().isBlank()) return null;
            try {
                return mapper.readValue(response.body(), type);
            } catch (IOException malformed) {
                throw new DomainException(HttpStatus.BAD_GATEWAY, "RAG_RESPONSE_INVALID", "Knowledge authority response was invalid");
            }
        } catch (DomainException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "RAG_SERVICE_UNAVAILABLE", "Knowledge authority is unavailable");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "RAG_SERVICE_UNAVAILABLE", "Knowledge authority request was interrupted");
        }
    }

    private void ensureReady(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        if (!properties.enabled() || !properties.tokenConfigured()) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "RAG_SERVICE_UNAVAILABLE", "Knowledge authority is not configured");
        }
    }

    private URI uri(String path, Map<String, String> query) {
        String base = properties.baseUrl().replaceAll("/+$", "");
        return URI.create(base + (path.startsWith("/") ? path : "/" + path) + queryString(query));
    }

    private static Map<String, String> query(String domain, String state) {
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        if (domain != null && !domain.isBlank()) values.put("domain", domain.trim());
        if (state != null && !state.isBlank()) values.put("state", state.trim());
        return values;
    }

    private static String queryString(Map<String, String> values) {
        if (values == null || values.isEmpty()) return "";
        StringJoiner joiner = new StringJoiner("&", "?", "");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            joiner.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return joiner.toString();
    }

    private HttpRequest.BodyPublisher bodyPublisher(Object body) {
        try {
            return HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "KNOWLEDGE_REQUEST_INVALID", "Knowledge request could not be serialized");
        }
    }

    private DomainException problem(int statusCode, String body) {
        HttpStatus status;
        try {
            status = HttpStatus.valueOf(statusCode);
        } catch (IllegalArgumentException invalid) {
            status = HttpStatus.BAD_GATEWAY;
        }
        String code = status.is5xxServerError() ? "RAG_SERVICE_UNAVAILABLE" : "KNOWLEDGE_AUTHORITY_ERROR";
        String message = status.is5xxServerError() ? "Knowledge authority is unavailable" : "Knowledge request was rejected";
        if (body != null && !body.isBlank()) {
            try {
                ApiError error = mapper.readValue(body, ApiError.class);
                if (error.code() != null && !error.code().isBlank()) code = error.code();
                if (error.message() != null && !error.message().isBlank()) message = error.message();
            } catch (IOException ignored) {
                // Never reflect an upstream body or provider detail to callers.
            }
        }
        return new DomainException(status, code, message);
    }
}
