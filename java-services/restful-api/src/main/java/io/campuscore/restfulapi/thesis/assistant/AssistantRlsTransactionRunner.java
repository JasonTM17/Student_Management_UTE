package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.AssistantRlsBoundary.Access;
import java.sql.PreparedStatement;
import java.util.function.Supplier;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Starts an Assistant transaction, binds its trusted context locally, then runs the protected SQL operation. */
public final class AssistantRlsTransactionRunner {
    @FunctionalInterface
    public interface Work<T> {
        T run() throws Throwable;
    }

    private final TransactionTemplate transaction;
    private final NamedParameterJdbcTemplate jdbc;
    private final boolean testMode;

    public AssistantRlsTransactionRunner(PlatformTransactionManager transactionManager,
            NamedParameterJdbcTemplate jdbc, boolean testMode) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.jdbc = jdbc;
        this.testMode = testMode;
    }

    public <T> T execute(Access access, Work<T> work) throws Throwable {
        AssistantRlsContext.Identity active = AssistantRlsContext.current();
        AssistantRlsContext.Identity identity;
        if (testMode) {
            identity = active;
        } else if (active != null) {
            requireCompatible(access, active);
            identity = active;
        } else {
            identity = AssistantRlsContext.forAccess(access);
        }

        if (testMode) {
            return executeTransaction(identity, work);
        }
        try {
            return AssistantRlsContext.withIdentity(identity, () -> {
                try {
                    return executeTransaction(identity, work);
                } catch (Throwable failure) {
                    throw new AssistantInvocationFailure(failure);
                }
            });
        } catch (AssistantInvocationFailure failure) {
            throw failure.getCause();
        }
    }

    public <T> T executeUnchecked(Access access, Supplier<T> work) {
        try {
            return execute(access, work::get);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Assistant transaction failed", failure);
        }
    }

    private <T> T executeTransaction(AssistantRlsContext.Identity identity, Work<T> work) throws Throwable {
        Outcome<T> outcome = transaction.execute(status -> {
            if (!testMode) {
                if (identity == null) {
                    throw new IllegalStateException("Assistant database context is missing");
                }
                bindTransactionContext(identity);
            }
            try {
                return new Outcome<>(work.run(), null);
            } catch (Throwable failure) {
                status.setRollbackOnly();
                return new Outcome<>(null, failure);
            }
        });
        if (outcome == null) {
            return null;
        }
        if (outcome.failure() != null) {
            throw outcome.failure();
        }
        return outcome.value();
    }

    private void bindTransactionContext(AssistantRlsContext.Identity identity) {
        jdbc.getJdbcTemplate().execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT set_config('app.assistant.owner_id', ?, true), "
                            + "set_config('app.assistant.scope', ?, true), "
                            + "set_config('app.assistant.admin', ?, true)")) {
                statement.setString(1, identity.ownerId() == null ? "" : identity.ownerId());
                statement.setString(2, identity.scope().name());
                statement.setString(3, Boolean.toString(identity.admin()));
                statement.execute();
            }
            return null;
        });
    }

    private static void requireCompatible(Access access, AssistantRlsContext.Identity active) {
        boolean compatible = switch (access) {
            case AUTO -> true;
            case ADMIN_GOVERNANCE -> active.scope() == AssistantRlsContext.Scope.ADMIN_GOVERNANCE;
            case RETENTION -> active.scope() == AssistantRlsContext.Scope.RETENTION;
            case KNOWLEDGE_PROJECTION -> active.scope() == AssistantRlsContext.Scope.KNOWLEDGE_PROJECTION;
        };
        if (!compatible) {
            throw new IllegalStateException("Assistant RLS scope cannot be escalated or changed within an active transaction");
        }
    }

    private record Outcome<T>(T value, Throwable failure) { }

    private static final class AssistantInvocationFailure extends RuntimeException {
        private AssistantInvocationFailure(Throwable cause) {
            super(cause);
        }
    }
}
