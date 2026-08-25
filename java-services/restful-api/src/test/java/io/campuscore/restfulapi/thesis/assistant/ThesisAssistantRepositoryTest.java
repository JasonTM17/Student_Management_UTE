package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantJpaGateway;
import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantJpaGateway.Parameters;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ThesisAssistantRepositoryTest {

    @Test
    void retentionExpiryUsesExplicitSqlTimestampForPostgres() {
        AssistantJpaGateway jpa = org.mockito.Mockito.mock(AssistantJpaGateway.class);
        when(jpa.update(anyString(), any(Parameters.class))).thenReturn(1);

        ThesisAssistantRepository repository = new ThesisAssistantRepository(jpa);
        UUID conversationId = repository.ensureConversation("owner-a", null, "vi", 90);

        org.mockito.ArgumentCaptor<Parameters> parameters =
                org.mockito.ArgumentCaptor.forClass(Parameters.class);
        verify(jpa).update(anyString(), parameters.capture());

        org.junit.jupiter.api.Assertions.assertEquals(
                conversationId,
                parameters.getValue().getValue("id"));
        assertInstanceOf(Timestamp.class, parameters.getValue().getValue("expires"));
    }
}
