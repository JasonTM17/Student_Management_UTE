package io.campuscore.restfulapi.system.job;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcOperations;

class DatabaseKeepAliveSchedulerTest {

    @Test
    @DisplayName("pingDatabase executes lightweight SELECT 1 query")
    void pingDatabaseExecutesSelectOne() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);

        DatabaseKeepAliveScheduler scheduler = new DatabaseKeepAliveScheduler(new io.campuscore.restfulapi.system.repository.DatabaseProbeRepository(jdbc));
        scheduler.pingDatabase();

        verify(jdbc).queryForObject("SELECT 1", Integer.class);
    }

    @Test
    @DisplayName("pingDatabase handles DataAccessException gracefully without throwing")
    void pingDatabaseHandlesDataAccessExceptionGracefully() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.queryForObject(any(String.class), eq(Integer.class)))
                .thenThrow(new DataAccessResourceFailureException("Connection refused"));

        DatabaseKeepAliveScheduler scheduler = new DatabaseKeepAliveScheduler(new io.campuscore.restfulapi.system.repository.DatabaseProbeRepository(jdbc));
        assertThatCode(scheduler::pingDatabase).doesNotThrowAnyException();
    }
}
