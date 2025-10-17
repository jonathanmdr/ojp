package org.openjproxy.grpc.server.xa;

import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages XA sessions on the server side.
 * Maps XA session UUIDs to XA sessions.
 */
@Slf4j
public class XaSessionManager {
    
    private final Map<String, XaSession> xaSessions = new ConcurrentHashMap<>();

    /**
     * Register a new XA session.
     */
    public void registerSession(XaSession session) {
        log.debug("Registering XA session: {}", session.getXaSessionUUID());
        xaSessions.put(session.getXaSessionUUID(), session);
    }

    /**
     * Get an XA session by UUID.
     */
    public XaSession getSession(String xaSessionUUID) throws SQLException {
        XaSession session = xaSessions.get(xaSessionUUID);
        if (session == null) {
            throw new SQLException("XA session not found: " + xaSessionUUID);
        }
        if (session.isClosed()) {
            throw new SQLException("XA session is closed: " + xaSessionUUID);
        }
        return session;
    }

    /**
     * Remove and close an XA session.
     */
    public void closeSession(String xaSessionUUID) throws SQLException {
        log.debug("Closing XA session: {}", xaSessionUUID);
        XaSession session = xaSessions.remove(xaSessionUUID);
        if (session != null) {
            session.close();
        }
    }

    /**
     * Get all XA sessions.
     */
    public Map<String, XaSession> getAllSessions() {
        return new ConcurrentHashMap<>(xaSessions);
    }

    /**
     * Close all XA sessions.
     */
    public void closeAllSessions() {
        log.info("Closing all XA sessions");
        for (XaSession session : xaSessions.values()) {
            try {
                session.close();
            } catch (Exception e) {
                log.error("Error closing XA session: " + session.getXaSessionUUID(), e);
            }
        }
        xaSessions.clear();
    }
}
