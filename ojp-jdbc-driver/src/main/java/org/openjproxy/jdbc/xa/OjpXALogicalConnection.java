package org.openjproxy.jdbc.xa;

import com.openjproxy.grpc.SessionInfo;
import lombok.extern.slf4j.Slf4j;
import org.openjproxy.database.DatabaseUtils;
import org.openjproxy.jdbc.Connection;

import java.sql.SQLException;

/**
 * Logical connection that wraps the XA session on the server.
 * This connection delegates to the server-side XA connection for all operations,
 * but ensures that commits and rollbacks are controlled by the XA resource.
 */
@Slf4j
class OjpXALogicalConnection extends Connection {

    private final OjpXAConnection xaConnection;
    private boolean closed = false;

    OjpXALogicalConnection(OjpXAConnection xaConnection, SessionInfo sessionInfo, String url) throws SQLException {
        // Pass the statementService and dbName to the parent Connection class
        super(sessionInfo, xaConnection.getStatementService(), DatabaseUtils.resolveDbName(url));
        this.xaConnection = xaConnection;
        
        log.debug("Created logical connection using XA session: {}", sessionInfo.getSessionUUID());
    }

    @Override
    public void close() throws SQLException {
        log.debug("Logical connection close called");
        if (!closed) {
            closed = true;
            // Don't close the underlying XA connection - just mark this logical connection as closed
            // The actual XA connection will be closed when XAConnection.close() is called
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
        // XA connections ignore auto-commit settings as they are controlled by XA protocol
        // This is required for compatibility with transaction managers like Atomikos
        // that may call setAutoCommit(true) during connection lifecycle management
        log.debug("setAutoCommit({}) called on XA connection - ignored (XA protocol controls transaction)", autoCommit);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        // XA connections are always non-auto-commit
        return false;
    }
}
