package org.openjdbcproxy.grpc.server;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manages connection acquisition with enhanced timeout and monitoring capabilities.
 * This class wraps HikariCP connection acquisition to add additional safeguards
 * against blocking indefinitely under high load.
 * 
 * ISSUE #29 FIX: This class was created to resolve the problem where OJP would
 * block indefinitely under high concurrent load (200+ threads) when the HikariCP
 * connection pool was exhausted. The solution uses CompletableFuture with timeout
 * to ensure connections are acquired within a reasonable time or fail gracefully.
 * 
 * @see <a href="https://github.com/Open-JDBC-Proxy/ojp/issues/29">Issue #29</a>
 */
@Slf4j
public class ConnectionAcquisitionManager {
    
    private static final long ACQUISITION_TIMEOUT_MS = 15000; // 15 seconds max wait
    
    /**
     * Acquires a connection from the given datasource with enhanced timeout handling.
     * This method provides additional protection against indefinite blocking by using
     * CompletableFuture with a timeout.
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
        
        // Use CompletableFuture to add our own timeout layer on top of HikariCP's timeout
        CompletableFuture<Connection> connectionFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        
        try {
            Connection connection = connectionFuture.get(ACQUISITION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            log.debug("Successfully acquired connection for hash: {} in thread: {}", 
                connectionHash, Thread.currentThread().getName());
            return connection;
            
        } catch (TimeoutException e) {
            // Cancel the future to prevent resource leaks
            connectionFuture.cancel(true);
            
            // Log detailed information about the timeout
            String errorMsg = String.format(
                "Connection acquisition timeout (%dms) for hash: %s. Pool state - Active: %d, Max: %d, Waiting threads: %d",
                ACQUISITION_TIMEOUT_MS, 
                connectionHash,
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getMaximumPoolSize(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
            );
            log.error(errorMsg);
            throw new SQLException(errorMsg, "08001"); // Connection exception SQL state
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            connectionFuture.cancel(true);
            throw new SQLException("Connection acquisition interrupted for hash: " + connectionHash, e);
            
        } catch (ExecutionException e) {
            connectionFuture.cancel(true);
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException && cause.getCause() instanceof SQLException) {
                SQLException sqlException = (SQLException) cause.getCause();
                
                // Enhanced error message with pool statistics
                String enhancedMessage = String.format(
                    "Connection acquisition failed for hash: %s. Pool state - Active: %d, Max: %d. Original error: %s",
                    connectionHash,
                    dataSource.getHikariPoolMXBean().getActiveConnections(),
                    dataSource.getMaximumPoolSize(),
                    sqlException.getMessage()
                );
                
                log.error(enhancedMessage);
                throw new SQLException(enhancedMessage, sqlException.getSQLState(), sqlException);
            } else {
                throw new SQLException("Unexpected error during connection acquisition for hash: " + connectionHash, cause);
            }
        }
    }
}