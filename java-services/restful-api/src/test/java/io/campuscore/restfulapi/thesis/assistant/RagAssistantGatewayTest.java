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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import io.campuscore.restfulapi.web.DomainException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RagAssistantGatewayTest {
    private HttpServer server;

    @Test
    void stalledSseBodyEndsWithinTheAbsoluteReadDeadline() throws Exception {
        assertBodyReadDeadline(false);
    }

    @Test
    void heartbeatOnlySseBodyCannotExtendTheReadDeadline() throws Exception {
        assertBodyReadDeadline(true);
    }

    private void assertBodyReadDeadline(boolean heartbeatOnly) throws Exception {
        CountDownLatch headersFlushed = new CountDownLatch(1);
        CountDownLatch releaseBody = new CountDownLatch(1);
        startServer(exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try {
                var output = exchange.getResponseBody();
                output.write((heartbeatOnly ? ":heartbeat\n\n" : "event:meta\ndata:{}\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                output.flush();
                headersFlushed.countDown();
                while (!releaseBody.await(100, TimeUnit.MILLISECONDS)) {
                    if (heartbeatOnly) {
                        output.write(":heartbeat\n\n".getBytes(StandardCharsets.UTF_8));
                        output.flush();
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        }, "/chat/stream");
        RagAssistantGateway gateway = gateway(1_000);
        var reader = Executors.newSingleThreadExecutor();
        try {
            var result = reader.submit(() -> {
                try {
                    gateway.stream(new ChatRequest("Quy chế học vụ", "vi"), "owner-a", event -> { });
                    return null;
                } catch (DomainException failure) {
                    return failure;
                }
            });
            assertTrue(headersFlushed.await(2, TimeUnit.SECONDS));
            DomainException failure = result.get(2_500, TimeUnit.MILLISECONDS);
            assertInstanceOf(DomainException.class, failure);
            assertEquals("RAG_SERVICE_UNAVAILABLE", failure.code());
        } finally {
            releaseBody.countDown();
            reader.shutdownNow();
            assertTrue(reader.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void acceptedTerminalReleasesAnOpenSseBodyImmediately() throws Exception {
        assertOpenTerminalBody("{\"reasonCode\":\"ANSWERED\",\"degraded\":false,\"terminalStatus\":\"COMPLETED\"}", false);
    }

    @Test
    void malformedTerminalIsAnErrorRatherThanSuccessfulCompletion() throws Exception {
        assertOpenTerminalBody("{\"reasonCode\":", true);
    }

    private void assertOpenTerminalBody(String terminalJson, boolean malformed) throws Exception {
        CountDownLatch releaseBody = new CountDownLatch(1);
        startServer(exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try {
                exchange.getResponseBody().write(("event:meta\ndata:{}\n\nevent:delta\ndata:{\"sequence\":0,"
                        + "\"text\":\"Grounded answer\",\"sourceIds\":[]}\n\nevent:done\ndata:"
                        + terminalJson + "\n\n").getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                releaseBody.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        }, "/chat/stream");
        var reader = Executors.newSingleThreadExecutor();
        List<ThesisAssistantService.StreamEvent> events = new ArrayList<>();
        try {
            var result = reader.submit(() -> {
                try {
                    gateway().stream(new ChatRequest("Quy chế học vụ", "vi"), "owner-a", events::add);
                    return null;
                } catch (DomainException failure) {
                    return failure;
                }
            });
            DomainException failure = result.get(1_500, TimeUnit.MILLISECONDS);
            if (malformed) {
                assertInstanceOf(DomainException.class, failure);
                assertEquals("RAG_SERVICE_UNAVAILABLE", failure.code());
                assertTrue(events.stream().noneMatch(ThesisAssistantService.StreamDone.class::isInstance));
            } else {
                assertEquals(null, failure);
                assertEquals(1, events.stream().filter(ThesisAssistantService.StreamDone.class::isInstance).count());
            }
        } finally {
            releaseBody.countDown();
            reader.shutdownNow();
            assertTrue(reader.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

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
    void chatReplacesUnsafeRemoteAnswerBeforeReturningToThePublicApi() throws Exception {
        startServer(exchange -> {
            byte[] response = """
                    {"answer":"The retrieved context contains curl commands and API endpoints.","model":"deepseek-v4-flash","degraded":false,"reasonCode":"ANSWERED","locale":"vi","citations":[]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }, "/chat");

        var response = gateway().chat(new ChatRequest("Câu hỏi", "vi", UUID.randomUUID(), null), "owner-a");

        assertEquals(ThesisAssistantService.technicalOutputMessage("vi"), response.answer());
        assertEquals("PROVIDER_UNSAFE_OUTPUT", response.reasonCode());
        assertTrue(response.degraded());
    }

    @Test
    void chatNormalizesRemoteCopyAndRemovesCitationsFromNoMatch() throws Exception {
        var citation = """
                {"id":"doc-1","slug":"registration","title":"Registration","source":"office","locale":"vi","excerpt":"Public guidance","domain":"ACADEMIC","sourceKind":"CURATED","snapshotHash":"hash"}
                """;
        startServer(exchange -> {
            byte[] response = ("""
                    {"answer":"1. Đăng ký lớp Mở Cổng sinh viên tương đương4.0","model":"deepseek-v4-flash","degraded":false,"reasonCode":"ANSWERED","locale":"vi","citations":[%s]}
                    """.formatted(citation)).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }, "/chat");

        var answered = gateway().chat(new ChatRequest("Đăng ký", "vi", UUID.randomUUID(), null), "owner-a");
        assertEquals("1. Đăng ký lớp\n\nMở Cổng sinh viên tương đương 4.0", answered.answer());
        assertEquals(1, answered.citations().size());

        stopServer();
        startServer(exchange -> {
            byte[] response = ("""
                    {"answer":"Mình chưa tìm thấy hướng dẫn phù hợp.","model":"curated-lexical-rag","degraded":false,"reasonCode":"NO_MATCH","locale":"vi","citations":[%s]}
                    """.formatted(citation)).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }, "/chat");

        var noMatch = gateway().chat(new ChatRequest("Thời tiết", "vi", UUID.randomUUID(), null), "owner-a");
        assertEquals("NO_MATCH", noMatch.reasonCode());
        assertTrue(noMatch.citations().isEmpty());
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

    @Test
    void streamReplacesAFragmentedRemoteCommandBeforeForwardingIt() throws Exception {
        UUID messageId = UUID.randomUUID();
        startServer(exchange -> {
            byte[] response = ("""
                    event: delta
                    data: {"sequence":0,"text":"Here is a command: cur","sourceIds":[],"type":"delta"}

                    event: delta
                    data: {"sequence":1,"text":"l https://campuscore.local/api/v1/assistant","sourceIds":[],"type":"delta"}

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

        assertTrue(events.stream().noneMatch(event -> event instanceof ThesisAssistantService.StreamDelta delta
                && (delta.text().contains("curl") || delta.text().contains("/api/v1"))));
        ThesisAssistantService.StreamReplace replace = assertInstanceOf(
                ThesisAssistantService.StreamReplace.class,
                events.stream().filter(ThesisAssistantService.StreamReplace.class::isInstance).findFirst().orElseThrow());
        assertEquals(ThesisAssistantService.technicalOutputMessage("vi"), replace.text());
        ThesisAssistantService.StreamDone done = assertInstanceOf(
                ThesisAssistantService.StreamDone.class,
                events.stream().filter(ThesisAssistantService.StreamDone.class::isInstance).findFirst().orElseThrow());
        assertEquals("PROVIDER_UNSAFE_OUTPUT", done.reasonCode());
        assertTrue(done.degraded());
    }

    @Test
    void streamForwardsProviderDeltasLiveWithoutReEmittingTheAnswerAtDone() throws Exception {
        UUID messageId = UUID.randomUUID();
        startServer(exchange -> {
            byte[] response = ("""
                    event: delta
                    data: {"sequence":0,"text":"1. Đăng ký lớp Mở Cổng sinh viên tương đương4.0","sourceIds":["doc-1"],"type":"delta"}

                    event: citation
                    data: {"citation":{"id":"doc-1","slug":"registration","title":"Registration","source":"office","locale":"vi","excerpt":"Public guidance","domain":"ACADEMIC","sourceKind":"CURATED","snapshotHash":"hash"},"type":"citation"}

                    event: done
                    data: {"messageId":"%s","reasonCode":"NO_MATCH","degraded":false,"terminalStatus":"COMPLETED","type":"done"}

                    """.formatted(messageId)).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }, "/chat/stream");

        List<ThesisAssistantService.StreamEvent> events = new ArrayList<>();
        gateway().stream(new ChatRequest("Thời tiết", "vi", UUID.randomUUID(), null), "owner-a", events::add);

        // The provider delta reaches the client live and exactly once: the
        // done frame no longer re-emits the whole answer, or the client would
        // render the response twice.
        List<ThesisAssistantService.StreamDelta> deltas = events.stream()
                .filter(ThesisAssistantService.StreamDelta.class::isInstance)
                .map(ThesisAssistantService.StreamDelta.class::cast)
                .toList();
        assertEquals(1, deltas.size());
        assertEquals("1. Đăng ký lớp Mở Cổng sinh viên tương đương4.0", deltas.get(0).text());
        assertTrue(events.stream().noneMatch(ThesisAssistantService.StreamCitation.class::isInstance));
        assertEquals(1, events.stream().filter(ThesisAssistantService.StreamDone.class::isInstance).count());
    }

    @Test
    void streamedContentIsNotDuplicatedByTheDoneFrame() throws Exception {
        startServer(exchange -> {
            byte[] response = """
                    event: delta
                    data: {"sequence":0,"text":"Phần một.","sourceIds":[],"type":"delta"}

                    event: delta
                    data: {"sequence":1,"text":" Phần hai.","sourceIds":[],"type":"delta"}

                    event: done
                    data: {"messageId":null,"reasonCode":null,"degraded":false,"terminalStatus":"COMPLETED","type":"done"}

                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }, "/chat/stream");

        List<ThesisAssistantService.StreamEvent> events = new ArrayList<>();
        gateway().stream(new ChatRequest("Quy chế", "vi", UUID.randomUUID(), null), "owner-a", events::add);

        List<ThesisAssistantService.StreamDelta> deltas = events.stream()
                .filter(ThesisAssistantService.StreamDelta.class::isInstance)
                .map(ThesisAssistantService.StreamDelta.class::cast)
                .toList();
        assertEquals(2, deltas.size());
        assertEquals("Phần một. Phần hai.",
                deltas.get(0).text() + deltas.get(1).text());
        assertEquals(1, events.stream().filter(ThesisAssistantService.StreamDone.class::isInstance).count());
    }

    private RagAssistantGateway gateway() {
        return gateway(30_000);
    }

    private RagAssistantGateway gateway(int readTimeoutMs) {
        int port = server.getAddress().getPort();
        return new RagAssistantGateway(new AssistantRagProperties(
                "http://127.0.0.1:" + port,
                "internal-token",
                false,
                1_000,
                readTimeoutMs),
                new ObjectMapper());
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler, String path) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(path, handler);
        server.start();
    }
}
