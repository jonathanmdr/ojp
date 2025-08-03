package org.openjdbcproxy.grpc.server.utils;

import com.openjdbcproxy.grpc.SessionInfo;

/**
 * Utility class for creating SessionInfo builders.
 * Extracted from StatementServiceImpl to improve modularity.
 */
public class SessionInfoUtils {

    /**
     * Creates a new SessionInfo builder from an existing SessionInfo.
     *
     * @param activeSessionInfo The source session info
     * @return A new builder with copied values
     */
    public static SessionInfo.Builder newBuilderFrom(SessionInfo activeSessionInfo) {
        return SessionInfo.newBuilder()
                .setConnHash(activeSessionInfo.getConnHash())
                .setClientUUID(activeSessionInfo.getClientUUID())
                .setSessionUUID(activeSessionInfo.getSessionUUID())
                .setSessionStatus(activeSessionInfo.getSessionStatus())
                .setTransactionInfo(activeSessionInfo.getTransactionInfo());
    }
}