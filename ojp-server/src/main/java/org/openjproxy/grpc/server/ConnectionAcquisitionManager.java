package org.openjproxy.grpc.server;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Manages connection acquisition with enhanced monitoring capabilities.
 * This class wraps HikariCP connection acquisition to provide better error messages
 * and pool state information when connection acquisition fails.
 * 
 * ISSUE #29 FIX: This class was created to resolve the problem where OJP would
 * block indefinitely under high concurrent load (200+ threads) when the HikariCP
 * connection pool was exhausted. The solution relies on HikariCP's built-in timeout
 * mechanisms while providing enhanced error reporting with pool statistics.
 * 
 * @see <a href="https://github.com/Open-J-Proxy/ojp/issues/29">Issue #29</a>
 */
@Slf4j
public class ConnectionAcquisitionManager {
    
    /**
     * Acquires a connection from the given datasource with enhanced error reporting.
     * This method relies on HikariCP's built-in connection timeout mechanism to prevent
     * indefinite blocking, while providing detailed error messages with pool statistics.
     * 
     * @param dataSource the HikariCP datasource
     * @param connectionHash the connection hash for logging purposes
     * @return a database connection
     * @throws SQLException if connection acquisition fails or times out
     */
    public static Connection acquireConnection(HikariDataSource dataSource, String connectionHash) throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource is null for connection hash: " + connectionHash);
        }
        
        // Log current pool state before attempting acquisition
        try {
            log.debug("Connection acquisition attempt for hash: {} - Active: {}, Idle: {}, Total: {}, Waiting: {}", 
                connectionHash,
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(), 
                dataSource.getHikariPoolMXBean().getTotalConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
        } catch (Exception e) {
            log.debug("Could not retrieve pool statistics for hash: {}", connectionHash);
        }
        
        try {
            // Use HikariCP's built-in connection timeout - this prevents indefinite blocking
            // The timeout is configured via ConnectionPoolConfigurer (default: 10 seconds)
            Connection connection = dataSource.getConnection();
            log.debug("Successfully acquired connection for hash: {} in thread: {}", 
                connectionHash, Thread.currentThread().getName());
            return connection;
            
        } catch (SQLException e) {
            // Enhanced error message with pool statistics
            String enhancedMessage;
            try {
                enhancedMessage = String.format(
                    "Connection acquisition failed for hash: %s. Pool state - Active: %d, Max: %d, Waiting threads: %d. Original error: %s",
                    connectionHash,
                    dataSource.getHikariPoolMXBean().getActiveConnections(),
                    dataSource.getMaximumPoolSize(),
                    dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection(),
                    e.getMessage()
                );
            } catch (Exception poolStatsException) {
                enhancedMessage = String.format(
                    "Connection acquisition failed for hash: %s. Could not retrieve pool statistics. Original error: %s",
                    connectionHash, e.getMessage()
                );
            }
            
            log.error(enhancedMessage);
            throw new SQLException(enhancedMessage, e.getSQLState(), e);
        }
    }
}