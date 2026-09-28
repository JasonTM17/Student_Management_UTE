package io.campuscore.restfulapi.thesis.assistant;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTransientConnectionException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * A fail-closed DataSource placeholder used when the Assistant runtime role is
 * not provisioned (missing password, wrong login, unreachable RLS boundary).
 *
 * <p>Every connection attempt fails with the stable SQLState {@code 08001} and
 * the {@code ASSISTANT_RLS_UNAVAILABLE} marker so the RLS verifier can flip
 * {@link AssistantRlsState} to DEGRADED and assistant chat endpoints can answer
 * 503 without killing the whole application boot.</p>
 */
public final class UnavailableAssistantDataSource implements DataSource {

    public static final String UNAVAILABLE_REASON = "ASSISTANT_RLS_UNAVAILABLE";

    private final String detail;

    public UnavailableAssistantDataSource(String detail) {
        this.detail = detail == null || detail.isBlank()
                ? "Assistant RLS runtime datasource is not provisioned"
                : detail;
    }

    @Override
    public Connection getConnection() throws SQLException {
        throw unavailable();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw unavailable();
    }

    private SQLException unavailable() {
        return new SQLTransientConnectionException(
                UNAVAILABLE_REASON + ": " + detail, "08001");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a wrapper for " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        // No logging sink: this datasource never opens a connection.
    }

    @Override
    public void setLoginTimeout(int seconds) {
        // Nothing to time out: connections are refused immediately.
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("The unavailable assistant datasource has no JDBC driver logger");
    }

    /** Compat shim so beans that declare destroyMethod="close" stay satisfied. */
    public void close() {
        // No pooled resources to release.
    }
}
