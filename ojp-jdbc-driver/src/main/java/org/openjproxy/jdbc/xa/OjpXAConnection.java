package org.openjproxy.jdbc.xa;

import com.google.protobuf.ByteString;
import com.openjproxy.grpc.xa.XaCloseRequest;
import com.openjproxy.grpc.xa.XaConnectionRequest;
import com.openjproxy.grpc.xa.XaConnectionResponse;
import lombok.extern.slf4j.Slf4j;
import org.openjproxy.grpc.SerializationHandler;
import org.openjproxy.grpc.client.XaService;
import org.openjproxy.jdbc.ClientUUID;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Implementation of XAConnection that connects to the OJP server for XA operations.
 */
@Slf4j
public class OjpXAConnection implements XAConnection {

    private final XaService xaService;
    private final String xaSessionUUID;
    private final String url;
    private final String user;
    private final String password;
    private final Properties properties;
    private Connection logicalConnection;
    private OjpXAResource xaResource;
    private boolean closed = false;
    private final List<ConnectionEventListener> listeners = new ArrayList<>();

    public OjpXAConnection(XaService xaService, String url, String user, String password, Properties properties) throws SQLException {
        log.debug("Creating OjpXAConnection for URL: {}", url);
        this.xaService = xaService;
        this.url = url;
        this.user = user;
        this.password = password;
        this.properties = properties;

        try {
            // Connect to server and get XA session UUID
            ByteString propertiesBytes = ByteString.EMPTY;
            if (properties != null && !properties.isEmpty()) {
                propertiesBytes = ByteString.copyFrom(SerializationHandler.serialize(properties));
            }

            XaConnectionRequest request = XaConnectionRequest.newBuilder()
                    .setUrl(url)
                    .setUser(user != null ? user : "")
                    .setPassword(password != null ? password : "")
                    .setClientUUID(ClientUUID.getUUID())
                    .setProperties(propertiesBytes)
                    .build();

            XaConnectionResponse response = xaService.xaConnect(request);
            this.xaSessionUUID = response.getXaSessionUUID();
            log.debug("XA connection established with session UUID: {}", xaSessionUUID);

        } catch (Exception e) {
            log.error("Failed to create XA connection", e);
            throw new SQLException("Failed to create XA connection", e);
        }
    }

    @Override
    public XAResource getXAResource() throws SQLException {
        log.debug("getXAResource called");
        checkClosed();
        if (xaResource == null) {
            xaResource = new OjpXAResource(xaService, xaSessionUUID);
        }
        return xaResource;
    }

    @Override
    public Connection getConnection() throws SQLException {
        log.debug("getConnection called");
        checkClosed();
        
        // Close any existing logical connection
        if (logicalConnection != null && !logicalConnection.isClosed()) {
            logicalConnection.close();
        }
        
        // Create a new logical connection that wraps the physical XA connection
        // This connection should use the same XA session on the server
        logicalConnection = new OjpXALogicalConnection(this, url, user, password);
        return logicalConnection;
    }

    @Override
    public void close() throws SQLException {
        log.debug("close called");
        if (closed) {
            return;
        }
        
        closed = true;
        
        // Close logical connection if open
        if (logicalConnection != null && !logicalConnection.isClosed()) {
            logicalConnection.close();
        }
        
        // Notify listeners
        ConnectionEvent event = new ConnectionEvent(this);
        for (ConnectionEventListener listener : listeners) {
            listener.connectionClosed(event);
        }
        
        // Close XA session on server
        try {
            XaCloseRequest request = XaCloseRequest.newBuilder()
                    .setXaSessionUUID(xaSessionUUID)
                    .build();
            xaService.xaClose(request);
        } catch (Exception e) {
            log.error("Error closing XA session", e);
            throw new SQLException("Error closing XA session", e);
        }
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
        log.debug("addConnectionEventListener called");
        listeners.add(listener);
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        log.debug("removeConnectionEventListener called");
        listeners.remove(listener);
    }

    @Override
    public void addStatementEventListener(javax.sql.StatementEventListener listener) {
        log.debug("addStatementEventListener called - not supported");
        // Not supported for XA connections
    }

    @Override
    public void removeStatementEventListener(javax.sql.StatementEventListener listener) {
        log.debug("removeStatementEventListener called - not supported");
        // Not supported for XA connections
    }

    /**
     * Notify listeners of a connection error.
     */
    void notifyError(SQLException exception) {
        ConnectionEvent event = new ConnectionEvent(this, exception);
        for (ConnectionEventListener listener : listeners) {
            listener.connectionErrorOccurred(event);
        }
    }

    /**
     * Get the XA session UUID for this connection.
     */
    String getXaSessionUUID() {
        return xaSessionUUID;
    }

    private void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("XA Connection is closed");
        }
    }
}
