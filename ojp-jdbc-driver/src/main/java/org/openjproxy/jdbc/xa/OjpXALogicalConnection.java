package org.openjproxy.jdbc.xa;

import lombok.extern.slf4j.Slf4j;
import org.openjproxy.jdbc.Connection;
import org.openjproxy.jdbc.Driver;

import java.sql.SQLException;
import java.util.Properties;

/**
 * Logical connection that wraps a physical XA connection.
 * This connection delegates most operations to a standard OJP connection,
 * but ensures that commits and rollbacks are controlled by the XA resource.
 */
@Slf4j
class OjpXALogicalConnection extends Connection {

    private final OjpXAConnection xaConnection;
    private boolean closed = false;

    OjpXALogicalConnection(OjpXAConnection xaConnection, String url, String user, String password) throws SQLException {
        super(null, null, null);
        this.xaConnection = xaConnection;
        
        // Establish a regular connection to the server using the XA session
        // Note: This would ideally reuse the XA session on the server side
        // For now, we create a standard connection
        Properties info = new Properties();
        if (user != null) {
            info.setProperty("user", user);
        }
        if (password != null) {
            info.setProperty("password", password);
        }
        
        try {
            Driver driver = new Driver();
            Connection actualConnection = (Connection) driver.connect(url, info);
            
            // Copy the session and service from the actual connection
            this.setSession(actualConnection.getSession());
        } catch (SQLException e) {
            log.error("Failed to create logical connection", e);
            throw e;
        }
    }

    @Override
    public void close() throws SQLException {
        log.debug("Logical connection close called");
        if (!closed) {
            closed = true;
            // Call parent's close to properly terminate the session on the server
            super.close();
        }
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public void commit() throws SQLException {
        log.debug("commit called on logical connection - should be controlled by XA");
        throw new SQLException("Commit not allowed on XA connection. Use XAResource.commit() instead.");
    }

    @Override
    public void rollback() throws SQLException {
        log.debug("rollback called on logical connection - should be controlled by XA");
        throw new SQLException("Rollback not allowed on XA connection. Use XAResource.rollback() instead.");
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        if (autoCommit) {
            throw new SQLException("Cannot enable auto-commit on XA connection");
        }
        // Allow setting to false (XA connections should always be non-auto-commit)
        super.setAutoCommit(false);
    }
}
