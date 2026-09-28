package io.campuscore.restfulapi.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.thesis.assistant.AssistantRlsState;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.web.server.ResponseStatusException;

class HealthControllerTest {

    private final JdbcOperations jdbc = org.mockito.Mockito.mock(JdbcOperations.class);
    private final HealthController controller =
            new HealthController("health-key", jdbc, absentProvider());

    @Test
    void readinessReportsPostgresqlOnlyAfterTheProbeSucceeds() {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        Map<String, Object> response = controller.readiness("health-key");

        assertEquals("ready", response.get("status"));
        assertEquals(List.of("postgresql"), response.get("dependencies"));
        // Outside a persistence (non-test) deployment the RLS verdict is absent.
        assertFalse(response.containsKey("assistantRls"));
    }

    @Test
    void readinessReturnsServiceUnavailableWhenPostgresqlCannotBeReached() {
        when(jdbc.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("offline"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.readiness("health-key"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void readinessSurfacesVerifiedAssistantRlsStateWithoutFailing() {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        AssistantRlsState state = new AssistantRlsState();
        state.markVerified("role and policies verified");
        HealthController withState = new HealthController("health-key", jdbc, provider(state));

        Map<String, Object> response = withState.readiness("health-key");

        assertEquals("ready", response.get("status"));
        assertEquals("verified", response.get("assistantRls"));
        assertNull(response.get("assistantRlsReason"));
    }

    @Test
    void readinessSurfacesDegradedAssistantRlsStateWithReason() {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        AssistantRlsState state = new AssistantRlsState();
        state.markDegraded("Assistant runtime login credentials are required");
        HealthController withState = new HealthController("health-key", jdbc, provider(state));

        Map<String, Object> response = withState.readiness("health-key");

        // The primary readiness probe stays green: assistant degradation is a
        // feature-level 503, not a whole-service outage.
        assertEquals("ready", response.get("status"));
        assertEquals("degraded", response.get("assistantRls"));
        assertTrue(String.valueOf(response.get("assistantRlsReason"))
                .contains("credentials are required"));
    }

    private static ObjectProvider<AssistantRlsState> absentProvider() {
        return new ObjectProvider<>() {
            @Override
            public AssistantRlsState getObject() {
                throw new NoSuchBeanDefinitionException(AssistantRlsState.class);
            }
        };
    }

    private static ObjectProvider<AssistantRlsState> provider(AssistantRlsState state) {
        return new ObjectProvider<>() {
            @Override
            public AssistantRlsState getObject() {
                return state;
            }
        };
    }
}
