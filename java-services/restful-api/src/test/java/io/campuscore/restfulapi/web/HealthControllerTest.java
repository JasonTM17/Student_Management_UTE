package io.campuscore.restfulapi.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.web.server.ResponseStatusException;

class HealthControllerTest {

    private final JdbcOperations jdbc = org.mockito.Mockito.mock(JdbcOperations.class);
    private final HealthController controller = new HealthController("health-key", jdbc);

    @Test
    void readinessReportsPostgresqlOnlyAfterTheProbeSucceeds() {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        Map<String, Object> response = controller.readiness("health-key");

        assertEquals("ready", response.get("status"));
        assertEquals(List.of("postgresql"), response.get("dependencies"));
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
}
