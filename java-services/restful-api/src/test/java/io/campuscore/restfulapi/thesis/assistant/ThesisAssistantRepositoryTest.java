package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class ThesisAssistantRepositoryTest {

    @Test
    void retentionExpiryUsesExplicitSqlTimestampForPostgres() {
        NamedParameterJdbcTemplate jdbc = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        ThesisAssistantRepository repository = new ThesisAssistantRepository(jdbc);
        UUID conversationId = repository.ensureConversation("owner-a", null, "vi", 90);

        org.mockito.ArgumentCaptor<MapSqlParameterSource> parameters =
                org.mockito.ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), parameters.capture());

        org.junit.jupiter.api.Assertions.assertEquals(
                conversationId,
                parameters.getValue().getValue("id"));
        assertInstanceOf(Timestamp.class, parameters.getValue().getValue("expires"));
    }
}
