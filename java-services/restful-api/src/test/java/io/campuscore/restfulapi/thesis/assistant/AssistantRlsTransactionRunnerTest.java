package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.campuscore.restfulapi.thesis.assistant.AssistantRlsBoundary.Access;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Regression for the outage drill (CI "PostgreSQL and Compose runtime"): with
 * the database stopped, TransactionTemplate opens the connection BEFORE the
 * callback runs, so a dead pool used to surface as a raw
 * {@code CannotCreateTransactionException} — a TransactionException, not a
 * DataAccessException — and every `catch (DataAccessException)` degrade guard
 * in ThesisAssistantService missed it, answering HTTP 500 instead of the
 * designed degraded KNOWLEDGE_UNAVAILABLE contract.
 */
class AssistantRlsTransactionRunnerTest {

    @Test
    void aConnectionOpenFailureIsReportedInTheDataAccessExceptionFamily() {
        var dead = new UnavailableAssistantDataSource("unit test: pool refused the connection");
        var runner = new AssistantRlsTransactionRunner(
                new DataSourceTransactionManager(dead),
                new NamedParameterJdbcTemplate(new JdbcTemplate(dead)),
                true);

        var thrown = assertThrows(
                DataAccessResourceFailureException.class,
                () -> runner.executeUnchecked(Access.AUTO, () -> "never reached"));
        assertInstanceOf(CannotCreateTransactionException.class, thrown.getCause());
    }
}
