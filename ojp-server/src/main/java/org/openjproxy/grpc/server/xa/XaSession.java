package org.openjproxy.grpc.server.xa;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Holds information about an XA session on the server side.
 * Each XA session maps to an XAConnection from the underlying database driver.
 */
@Slf4j
public class XaSession {
    
    @Getter
    private final String xaSessionUUID;
    
    @Getter
    private final String connectionHash;
    
    @Getter
    private final String clientUUID;
    
    @Getter
    private final XAConnection xaConnection;
    
    @Getter
    private final XAResource xaResource;
    
    @Getter
    private Connection logicalConnection;
    
    private boolean closed;
    
    private int transactionTimeout = 0;

    public XaSession(XAConnection xaConnection, String connectionHash, String clientUUID) throws SQLException {
        this.xaConnection = xaConnection;
        this.connectionHash = connectionHash;
        this.clientUUID = clientUUID;
        this.xaSessionUUID = UUID.randomUUID().toString();
        this.xaResource = xaConnection.getXAResource();
        this.closed = false;
        log.debug("Created XA session with UUID: {}", xaSessionUUID);
    }

    /**
     * Get a logical connection for executing SQL statements.
     * This connection is bound to the XA transaction context.
     */
    public Connection getOrCreateLogicalConnection() throws SQLException {
        if (logicalConnection == null || logicalConnection.isClosed()) {
            logicalConnection = xaConnection.getConnection();
            log.debug("Created new logical connection for XA session: {}", xaSessionUUID);
        }
        return logicalConnection;
    }

    /**
     * Close the XA session and release all resources.
     */
    public void close() throws SQLException {
        if (closed) {
            return;
        }
        
        closed = true;
        log.debug("Closing XA session: {}", xaSessionUUID);
        
        // Close logical connection if open
        if (logicalConnection != null && !logicalConnection.isClosed()) {
            try {
                logicalConnection.close();
            } catch (SQLException e) {
                log.error("Error closing logical connection", e);
            }
        }
        
        // Close XA connection
        try {
            xaConnection.close();
        } catch (SQLException e) {
            log.error("Error closing XA connection", e);
            throw e;
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public void setTransactionTimeout(int seconds) {
        this.transactionTimeout = seconds;
    }

    public int getTransactionTimeout() {
        return transactionTimeout;
    }
}
