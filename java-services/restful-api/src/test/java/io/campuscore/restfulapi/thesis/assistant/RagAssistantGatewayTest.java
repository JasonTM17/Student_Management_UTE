package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RagAssistantGatewayTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void chatSendsInternalTokenAndOwnerToRagService() throws Exception {
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<String> owner = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        startServer(exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Rag-Service-Token"));
            owner.set(exchange.getRequestHeaders().getFirst("X-Assistant-Owner"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"answer":"ok","model":"curated-lexical-rag","degraded":false,"reasonCode":"ANSWERED","locale":"vi","citations":[]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }, "/chat");

        RagAssistantGateway gateway = gateway();
        var response = gateway.chat(new ChatRequest("Xin chào", "vi", UUID.randomUUID(), null), "owner-a");

        assertEquals("ok", response.answer());
        assertEquals("internal-token", token.get());
        assertEquals("owner-a", owner.get());
        assertTrue(body.get().contains("\"message\":\"Xin chào\""));
    }

    @Test
    void streamParsesRemoteSseEventsBackIntoAssistantEvents() throws Exception {
        UUID messageId = UUID.randomUUID();
        startServer(exchange -> {
            byte[] response = ("""
                    event: delta
                    data: {"sequence":0,"text":"xin chào","sourceIds":["doc-1"],"type":"delta"}

                    event: done
                    data: {"messageId":"%s","reasonCode":"ANSWERED","degraded":false,"terminalStatus":"COMPLETED","type":"done"}

                    """.formatted(messageId)).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }, "/chat/stream");

        List<ThesisAssistantService.StreamEvent> events = new ArrayList<>();
        gateway().stream(new ChatRequest("Stream?", "vi", UUID.randomUUID(), null), "owner-a", events::add);

        assertEquals(2, events.size());
        ThesisAssistantService.StreamDelta delta = assertInstanceOf(ThesisAssistantService.StreamDelta.class, events.get(0));
        ThesisAssistantService.StreamDone done = assertInstanceOf(ThesisAssistantService.StreamDone.class, events.get(1));
        assertEquals("xin chào", delta.text());
        assertEquals(messageId, done.messageId());
    }

    private RagAssistantGateway gateway() {
        int port = server.getAddress().getPort();
        return new RagAssistantGateway(new AssistantRagProperties(
                "http://127.0.0.1:" + port,
                "internal-token",
                false,
                1_000,
                30_000),
                new ObjectMapper());
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler, String path) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(path, handler);
        server.start();
    }
}
