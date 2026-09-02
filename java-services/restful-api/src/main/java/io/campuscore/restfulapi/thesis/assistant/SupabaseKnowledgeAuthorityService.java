package io.campuscore.restfulapi.thesis.assistant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantKnowledgeAdminController.KnowledgeDocumentView;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantKnowledgeAdminController.KnowledgeRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantKnowledgeAdminController.KnowledgeRevision;
import io.campuscore.restfulapi.web.DomainException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Server-side Supabase authoring adapter.  Never instantiate this in a web client. */
@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "assistant.knowledge", name = "authority-mode", havingValue = "supabase")
public class SupabaseKnowledgeAuthorityService {
    private final SupabaseKnowledgeProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public SupabaseKnowledgeAuthorityService(SupabaseKnowledgeProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .build());
    }

    SupabaseKnowledgeAuthorityService(SupabaseKnowledgeProperties properties, ObjectMapper mapper, HttpClient http) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = http;
    }

    public List<KnowledgeDocumentView> list(String actor, String domain, String state) {
        Map<String, String> payload = new java.util.LinkedHashMap<>();
        if (domain != null && !domain.isBlank()) payload.put("domain", domain.trim().toUpperCase(java.util.Locale.ROOT));
        if (state != null && !state.isBlank()) payload.put("state", state.trim().toUpperCase(java.util.Locale.ROOT));
        JsonNode result = call("LIST", null, payload, actor);
        try {
            return mapper.convertValue(result, new TypeReference<List<KnowledgeDocumentView>>() { });
        } catch (IllegalArgumentException malformed) {
            throw new DomainException(HttpStatus.BAD_GATEWAY, "KNOWLEDGE_AUTHORITY_INVALID", "Knowledge authority response was invalid");
        }
    }

    public KnowledgeDocumentView get(UUID id, String actor) {
        return convert(call("GET", id, Map.of(), actor), KnowledgeDocumentView.class);
    }

    public KnowledgeRevision create(KnowledgeRequest request, String actor) {
        return convert(call("CREATE", null, request, actor), KnowledgeRevision.class);
    }

    public KnowledgeRevision update(UUID id, KnowledgeRequest request, String actor) {
        return convert(call("UPDATE", id, request, actor), KnowledgeRevision.class);
    }

    public KnowledgeRevision submit(UUID id, String actor) {
        return convert(call("SUBMIT", id, Map.of(), actor), KnowledgeRevision.class);
    }

    public KnowledgeRevision publish(UUID id, String actor) {
        return convert(call("PUBLISH", id, Map.of(), actor), KnowledgeRevision.class);
    }

    public void archive(UUID id, String actor) {
        call("ARCHIVE", id, Map.of(), actor);
    }

    private JsonNode call(String action, UUID documentId, Object payload, String actor) {
        if (!properties.usable()) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "KNOWLEDGE_AUTHORITY_UNAVAILABLE", "Knowledge authority is not configured");
        }
        if (actor == null || actor.isBlank() || actor.length() > 120) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("p_action", action);
        body.put("p_document_id", documentId == null ? null : documentId.toString());
        body.put("p_payload", payload == null ? Map.of() : payload);
        body.put("p_actor", actor);
        String serialized;
        try {
            serialized = mapper.writeValueAsString(body);
        } catch (IOException impossible) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "KNOWLEDGE_REQUEST_INVALID", "Knowledge request could not be serialized");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.url().replaceAll("/+$", "") + "/rest/v1/rpc/knowledge_admin"))
                .timeout(Duration.ofMillis(properties.readTimeoutMs()))
                .header("apikey", properties.serviceRoleKey())
                .header("Authorization", "Bearer " + properties.serviceRoleKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Content-Profile", properties.schema())
                .header("Accept-Profile", properties.schema())
                .POST(HttpRequest.BodyPublishers.ofString(serialized, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw mapFailure(response.statusCode(), response.body());
            if (response.body() == null || response.body().isBlank()) return mapper.createObjectNode();
            return mapper.readTree(response.body());
        } catch (DomainException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "KNOWLEDGE_AUTHORITY_UNAVAILABLE", "Knowledge authority is unavailable");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "KNOWLEDGE_AUTHORITY_UNAVAILABLE", "Knowledge authority request was interrupted");
        }
    }

    private <T> T convert(JsonNode node, Class<T> type) {
        try {
            return mapper.treeToValue(node, type);
        } catch (IOException | IllegalArgumentException malformed) {
            throw new DomainException(HttpStatus.BAD_GATEWAY, "KNOWLEDGE_AUTHORITY_INVALID", "Knowledge authority response was invalid");
        }
    }

    private DomainException mapFailure(int status, String body) {
        String detail = body == null ? "" : body.toLowerCase(java.util.Locale.ROOT);
        if (detail.contains("knowledge_not_found") || status == 404) {
            return new DomainException(HttpStatus.NOT_FOUND, "KNOWLEDGE_NOT_FOUND", "Knowledge document was not found");
        }
        if (detail.contains("second_review") || detail.contains("second_admin") || detail.contains("state_conflict") || detail.contains("release_not_pending") || status == 409) {
            return new DomainException(HttpStatus.CONFLICT, detail.contains("second_review") || detail.contains("second_admin")
                    ? "KNOWLEDGE_SECOND_REVIEW_REQUIRED" : "KNOWLEDGE_STATE_CONFLICT", "Knowledge state does not permit this action");
        }
        if (detail.contains("privacy") || detail.contains("invalid") || status == 400) {
            return new DomainException(HttpStatus.BAD_REQUEST, "KNOWLEDGE_REQUEST_INVALID", "Knowledge request did not pass validation");
        }
        if (status >= 500) {
            return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "KNOWLEDGE_AUTHORITY_UNAVAILABLE", "Knowledge authority is unavailable");
        }
        return new DomainException(HttpStatus.BAD_GATEWAY, "KNOWLEDGE_AUTHORITY_ERROR", "Knowledge authority rejected the request");
    }
}
