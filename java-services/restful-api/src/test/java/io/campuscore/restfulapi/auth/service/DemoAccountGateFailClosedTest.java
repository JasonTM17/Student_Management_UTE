package io.campuscore.restfulapi.auth.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Fail-closed contract of the demo gate: a {@link DataAccessException} raised
 * while applying the configured state must propagate out of
 * {@link DemoAccountGate#run} so startup aborts. Swallowing it would leave the
 * seeded accounts in whatever status they happened to hold — potentially ACTIVE
 * on a host that asked for the demo to be off.
 *
 * <p>No Spring context or database: the JDBC template is mocked to fail on the
 * first statement, which exercises every branch (disabled, enabled-unscoped,
 * enabled-scoped) without an H2 fixture.
 */
class DemoAccountGateFailClosedTest {

    @Test
    void disabledModePropagatesDataAccessException() {
        DemoAccountGate gate = new DemoAccountGate(failingJdbc(), false, "STUDENT");

        assertThrows(DataAccessException.class, () -> gate.run(null));
    }

    @Test
    void enabledUnscopedModePropagatesDataAccessException() {
        DemoAccountGate gate = new DemoAccountGate(failingJdbc(), true, "");

        assertThrows(DataAccessException.class, () -> gate.run(null));
    }

    @Test
    void enabledScopedModePropagatesDataAccessException() {
        DemoAccountGate gate = new DemoAccountGate(failingJdbc(), true, "STUDENT");

        assertThrows(DataAccessException.class, () -> gate.run(null));
    }

    private static NamedParameterJdbcTemplate failingJdbc() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(anyString(), any(SqlParameterSource.class)))
                .thenThrow(new DataAccessResourceFailureException("simulated store failure"));
        return jdbc;
    }
}
