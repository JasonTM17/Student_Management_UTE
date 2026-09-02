package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantKnowledgeAdminController.KnowledgeRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantKnowledgeAdminController.KnowledgeRevision;
import io.campuscore.restfulapi.web.DomainException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SupabaseKnowledgeAuthorityServiceTest {
    @Test
    void createForwardsActorThroughServerSideRpcWithoutExposingKeyToPayload() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpRequest[] sent = new HttpRequest[1];
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            sent[0] = invocation.getArgument(0);
            return response(200, "{\"documentId\":\"11111111-1111-4111-8111-111111111111\",\"revisionId\":\"22222222-2222-4222-8222-222222222222\",\"version\":1,\"state\":\"DRAFT\"}");
        });
        ObjectMapper mapper = new ObjectMapper();
        SupabaseKnowledgeAuthorityService service = new SupabaseKnowledgeAuthorityService(properties(), mapper, http);

        KnowledgeRevision result = service.create(new KnowledgeRequest(
                "registration", "en", "Registration", "Public guidance", "registrar", 10, "REGISTRATION"), "admin-a");

        assertEquals("DRAFT", result.state());
        assertNotNull(sent[0]);
        assertEquals("service-key", sent[0].headers().firstValue("apikey").orElseThrow());
        JsonNode body = mapper.readTree(readBody(sent[0]));
        assertEquals("CREATE", body.path("p_action").asText());
        assertEquals("admin-a", body.path("p_actor").asText());
        assertEquals("REGISTRATION", body.path("p_payload").path("domain").asText());
        assertEquals(-1, body.toString().indexOf("service-key"));
    }

    @Test
    void secondAdminFailureIsMappedWithoutReflectingSupabaseBody() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> rejected = response(400,
                "{\"message\":\"second_admin_required\",\"hint\":\"secret internal detail\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(rejected);
        SupabaseKnowledgeAuthorityService service = new SupabaseKnowledgeAuthorityService(properties(), new ObjectMapper(), http);

        DomainException failure = assertThrows(DomainException.class,
                () -> service.publish(UUID.randomUUID(), "admin-a"));

        assertEquals("KNOWLEDGE_SECOND_REVIEW_REQUIRED", failure.code());
        assertEquals("Knowledge state does not permit this action", failure.getMessage());
    }

    @Test
    void upstreamFailureBecomesDegradedServiceError() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("network down"));
        SupabaseKnowledgeAuthorityService service = new SupabaseKnowledgeAuthorityService(properties(), new ObjectMapper(), http);

        DomainException failure = assertThrows(DomainException.class,
                () -> service.list("admin-a", null, null));

        assertEquals(503, failure.status().value());
        assertEquals("KNOWLEDGE_AUTHORITY_UNAVAILABLE", failure.code());
    }

    @Test
    void internalControllerRejectsInvalidRagTokenWith403() {
        SupabaseKnowledgeAuthorityService authority = mock(SupabaseKnowledgeAuthorityService.class);
        SupabaseKnowledgeSyncService sync = mock(SupabaseKnowledgeSyncService.class);
        AssistantRagProperties rag = new AssistantRagProperties("http://rag", "expected-token", true, 500, 1_000);
        SupabaseKnowledgeInternalController controller = new SupabaseKnowledgeInternalController(authority, sync, rag);

        DomainException failure = assertThrows(DomainException.class,
                () -> controller.list("wrong-token", "admin-a", null, null));

        assertEquals(403, failure.status().value());
        assertEquals("RAG_SERVICE_UNAUTHORIZED", failure.code());
    }

    private static SupabaseKnowledgeProperties properties() {
        return new SupabaseKnowledgeProperties(true, "https://project.supabase.co", "service-key",
                "assistant", "knowledge_release", "knowledge_release_document", 500, 1_000);
    }

    private static String readBody(HttpRequest request) {
        AtomicReference<String> value = new AtomicReference<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            private final StringBuilder buffer = new StringBuilder();
            private Flow.Subscription subscription;

            @Override public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }
            @Override public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                buffer.append(new String(bytes, StandardCharsets.UTF_8));
            }
            @Override public void onError(Throwable throwable) { throw new AssertionError(throwable); }
            @Override public void onComplete() { value.set(buffer.toString()); }
        });
        return value.get();
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
