package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import io.campuscore.restfulapi.web.DomainException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ThesisAssistantInternalControllerTest {

    @Test
    void rejectsMissingTokenBeforeCallingAssistantService() {
        ThesisAssistantService service = Mockito.mock(ThesisAssistantService.class);
        ThesisAssistantInternalController controller = controller(service);

        DomainException exception = assertThrows(DomainException.class,
                () -> controller.chat(new ChatRequest("Xin chào", "vi", UUID.randomUUID(), null), null, "owner-a"));

        assertEquals("RAG_SERVICE_UNAUTHORIZED", exception.code());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingOwnerBeforeCallingAssistantService() {
        ThesisAssistantService service = Mockito.mock(ThesisAssistantService.class);
        ThesisAssistantInternalController controller = controller(service);

        DomainException exception = assertThrows(DomainException.class,
                () -> controller.chat(new ChatRequest("Xin chào", "vi", UUID.randomUUID(), null), "internal-token", null));

        assertEquals("UNAUTHENTICATED", exception.code());
        verifyNoInteractions(service);
    }

    @Test
    void delegatesChatWithVerifiedInternalTokenAndOwner() {
        ThesisAssistantService service = Mockito.mock(ThesisAssistantService.class);
        ThesisAssistantInternalController controller = controller(service);
        UUID requestId = UUID.randomUUID();
        ChatRequest request = new ChatRequest("Xin chào", "vi", requestId, null);
        ChatResponse expected = new ChatResponse("ok", "curated-lexical-rag", false, "ANSWERED", "vi", List.of());
        when(service.answer("Xin chào", "vi", null, "owner-a", requestId)).thenReturn(expected);

        ChatResponse response = controller.chat(request, "internal-token", "owner-a");

        assertEquals(expected, response);
        verify(service).answer("Xin chào", "vi", null, "owner-a", requestId);
    }

    private ThesisAssistantInternalController controller(ThesisAssistantService service) {
        return new ThesisAssistantInternalController(service,
                new AssistantRagProperties("", "internal-token", true, 1_000, 30_000));
    }
}
