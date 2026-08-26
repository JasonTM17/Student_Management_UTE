package io.campuscore.restfulapi.thesis.assistant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantController.CancelResponse;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantController.ConversationCreated;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantController.CreateConversationRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantController.FeedbackResponse;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.FeedbackRequest;
import io.campuscore.restfulapi.web.ApiExceptionHandler.ApiError;
import io.campuscore.restfulapi.web.DomainException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Profile("persistence")
public class RagAssistantGateway {
    private static final String OWNER_HEADER = "X-Assistant-Owner";
    private static final String TOKEN_HEADER = "X-Rag-Service-Token";

    private final AssistantRagProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RagAssistantGateway(AssistantRagProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .build();
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public boolean tokenConfigured() {
        return properties.tokenConfigured();
    }

    public boolean isTransientFailure(DomainException exception) {
        return exception != null && (exception.status() == HttpStatus.SERVICE_UNAVAILABLE
                || exception.status() == HttpStatus.GATEWAY_TIMEOUT);
    }

    public ChatResponse chat(ChatRequest request, String ownerId) {
        return exchangeJson("POST", "/chat", Map.of(), Map.of(), request, ownerId, new TypeReference<ChatResponse>() { }).data();
    }

    public ChatResponse complete(ChatRequest request, String ownerId) {
        return chat(request, ownerId);
    }

    public CancelResponse cancel(UUID clientRequestId, String ownerId) {
        return exchangeJson("POST", "/requests/{id}/cancel", Map.of("id", clientRequestId.toString()),
                Map.of(), null, ownerId, new TypeReference<CancelResponse>() { }).data();
    }

    public FeedbackResponse feedback(UUID messageId, FeedbackRequest request, String ownerId) {
        return exchangeJson("PUT", "/messages/{id}/feedback", Map.of("id", messageId.toString()),
                Map.of(), request, ownerId, new TypeReference<FeedbackResponse>() { }).data();
    }

    public void deleteFeedback(UUID messageId, String ownerId) {
        send("DELETE", "/messages/{id}/feedback", Map.of("id", messageId.toString()), null, ownerId);
    }

    public RemotePage<List<ThesisAssistantRepository.Conversation>> conversations(String ownerId, Integer limit, String cursor) {
        return exchangeJson("GET", "/conversations", Map.of(), query(limit, cursor), null, ownerId,
                new TypeReference<List<ThesisAssistantRepository.Conversation>>() { });
    }

    public ConversationCreated createConversation(CreateConversationRequest request, String ownerId) {
        return exchangeJson("POST", "/conversations", Map.of(), Map.of(), request, ownerId, new TypeReference<ConversationCreated>() { }).data();
    }

    public RemotePage<List<ThesisAssistantRepository.Message>> messages(UUID conversationId, String ownerId, Integer limit, String cursor) {
        return exchangeJson("GET", "/conversations/{id}/messages",
                Map.of("id", conversationId.toString()), query(limit, cursor), null, ownerId,
                new TypeReference<List<ThesisAssistantRepository.Message>>() { });
    }

    public void deleteConversation(UUID id, String ownerId) {
        send("DELETE", "/conversations/{id}", Map.of("id", id.toString()), null, ownerId);
    }

    public void stream(ChatRequest request, String ownerId, Consumer<ThesisAssistantService.StreamEvent> sink) {
        ensureReady();
        HttpRequest httpRequest = requestBuilder(uri("/chat/stream", Map.of(), Map.of()), ownerId, "text/event-stream", true)
                .POST(bodyPublisher(request))
                .build();
        try {
            HttpResponse<java.io.InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (!isSuccess(response.statusCode())) {
                throw problem(response.statusCode(), new String(response.body().readAllBytes(), StandardCharsets.UTF_8));
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                parseStream(reader, sink);
            }
        } catch (IOException exception) {
            throw unavailable("RAG_SERVICE_UNAVAILABLE", "RAG service request failed");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("RAG_SERVICE_UNAVAILABLE", "RAG service request was interrupted");
        }
    }

    private void send(String method, String path, Map<String, String> pathValues, Object body, String ownerId) {
        exchangeJson(method, path, pathValues, Map.of(), body, ownerId, null);
    }

    private <T> RemotePage<T> exchangeJson(String method, String path, Map<String, String> pathValues,
            Map<String, String> queryValues, Object body,
            String ownerId, TypeReference<T> type) {
        ensureReady();
        URI uri = uri(path, pathValues, queryValues);
        HttpRequest request = requestBuilder(uri, ownerId, "application/json", body != null)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : bodyPublisher(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (!isSuccess(response.statusCode())) {
                throw problem(response.statusCode(), response.body());
            }
            T value = null;
            if (type != null && response.body() != null && !response.body().isBlank()) {
                value = objectMapper.readValue(response.body(), type);
            }
            return new RemotePage<>(value, response.headers().firstValue("X-Next-Cursor").orElse(null));
        } catch (IOException exception) {
            throw unavailable("RAG_SERVICE_UNAVAILABLE", "RAG service request failed");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("RAG_SERVICE_UNAVAILABLE", "RAG service request was interrupted");
        }
    }

    private HttpRequest.Builder requestBuilder(URI uri, String ownerId, String accept, boolean jsonBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(properties.readTimeoutMs()))
                .header("Accept", accept)
                .header(TOKEN_HEADER, properties.serviceToken())
                .header(OWNER_HEADER, ownerId == null ? "" : ownerId);
        if (jsonBody) {
            builder.header("Content-Type", "application/json");
        }
        return builder;
    }

    private void ensureReady() {
        if (!enabled()) {
            throw unavailable("RAG_SERVICE_UNAVAILABLE", "RAG service is not configured");
        }
        if (!tokenConfigured()) {
            throw unavailable("RAG_SERVICE_TOKEN_MISSING", "RAG service token is not configured");
        }
    }

    private URI uri(String path, Map<String, String> pathValues, Map<String, String> queryValues) {
        String normalizedPath = replacePathValues(path, pathValues);
        String normalizedQuery = queryString(queryValues);
        String base = properties.baseUrl().endsWith("/")
                ? properties.baseUrl().substring(0, properties.baseUrl().length() - 1)
                : properties.baseUrl();
        return URI.create(base + normalizedPath + normalizedQuery);
    }

    private static String replacePathValues(String path, Map<String, String> values) {
        String normalizedPath = path == null || path.isBlank() ? "" : path;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String replacement = entry.getValue() == null ? "" : URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8);
            normalizedPath = normalizedPath.replace(placeholder, replacement);
        }
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return normalizedPath;
    }

    private static Map<String, String> query(Integer limit, String cursor) {
        return query(limit, cursor, Map.of());
    }

    private static Map<String, String> query(Integer limit, String cursor, Map<String, String> seed) {
        Map<String, String> values = new LinkedHashMap<>(seed);
        if (limit != null) {
            values.put("limit", limit.toString());
        }
        if (cursor != null && !cursor.isBlank()) {
            values.put("cursor", cursor);
        }
        return values;
    }

    private static String queryString(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("&", "?", "");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            joiner.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return joiner.toString();
    }

    private HttpRequest.BodyPublisher bodyPublisher(Object body) {
        try {
            return HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new DomainException(HttpStatus.BAD_GATEWAY, "RAG_REQUEST_SERIALIZATION_FAILED", "Failed to serialize assistant request");
        }
    }

    private void parseStream(BufferedReader reader, Consumer<ThesisAssistantService.StreamEvent> sink) throws IOException {
        String line;
        String eventName = null;
        StringBuilder data = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                emit(eventName, data.toString(), sink);
                eventName = null;
                data.setLength(0);
                continue;
            }
            if (line.startsWith("event:")) {
                eventName = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring(5).stripLeading());
            }
        }
        emit(eventName, data.toString(), sink);
    }

    private void emit(String eventName, String payload, Consumer<ThesisAssistantService.StreamEvent> sink) throws IOException {
        if (eventName == null || eventName.isBlank() || payload == null || payload.isBlank()) {
            return;
        }
        ThesisAssistantService.StreamEvent event = switch (eventName) {
            case "meta" -> decode(payload, ThesisAssistantService.StreamMeta.class);
            case "delta" -> decode(payload, ThesisAssistantService.StreamDelta.class);
            case "replace" -> decode(payload, ThesisAssistantService.StreamReplace.class);
            case "citation" -> decode(payload, ThesisAssistantService.StreamCitation.class);
            case "done" -> decode(payload, ThesisAssistantService.StreamDone.class);
            case "error" -> decode(payload, ThesisAssistantService.StreamError.class);
            default -> null;
        };
        if (event != null && sink != null) {
            sink.accept(event);
        }
    }

    private <T extends ThesisAssistantService.StreamEvent> T decode(String payload, Class<T> type) throws IOException {
        JsonNode node = objectMapper.readTree(payload);
        if (node instanceof ObjectNode objectNode) {
            objectNode.remove("type");
        }
        return objectMapper.treeToValue(node, type);
    }

    private DomainException problem(int statusCode, String body) {
        HttpStatus status = HttpStatus.valueOf(statusCode);
        String code = status.is5xxServerError() ? "RAG_SERVICE_UNAVAILABLE" : "RAG_SERVICE_ERROR";
        String message = status.getReasonPhrase();
        if (body != null && !body.isBlank()) {
            try {
                ApiError error = objectMapper.readValue(body, ApiError.class);
                if (error.code() != null && !error.code().isBlank()) {
                    code = error.code();
                }
                if (error.message() != null && !error.message().isBlank()) {
                    message = error.message();
                }
            } catch (IOException ignored) {
                // Keep the synthesized status message when the remote service
                // returns a non-CampusCore payload.
            }
        }
        return new DomainException(status, code, message);
    }

    private DomainException unavailable(String code, String message) {
        return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    public record RemotePage<T>(T data, String nextCursor) { }
}
