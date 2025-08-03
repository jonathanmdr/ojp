package org.openjdbcproxy.grpc.server.pool;

import com.openjdbcproxy.grpc.ConnectionDetails;
import com.zaxxer.hikari.HikariConfig;
import lombok.extern.slf4j.Slf4j;
import org.openjdbcproxy.constants.CommonConstants;

import java.util.Properties;

import static org.openjdbcproxy.grpc.SerializationHandler.deserialize;

/**
 * Utility class responsible for configuring HikariCP connection pools.
 * Extracted from StatementServiceImpl to reduce its responsibilities.
 */
@Slf4j
public class ConnectionPoolConfigurer {

    /**
     * Configures a HikariCP connection pool with connection details and client properties.
     *
     * @param config            The HikariConfig to configure
     * @param connectionDetails The connection details containing properties
     */
    public static void configureHikariPool(HikariConfig config, ConnectionDetails connectionDetails) {
        Properties clientProperties = extractClientProperties(connectionDetails);

        // Configure basic connection pool settings first
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        // Configure HikariCP pool settings using client properties or defaults
        config.setMaximumPoolSize(getIntProperty(clientProperties, "ojp.connection.pool.maximumPoolSize", CommonConstants.DEFAULT_MAXIMUM_POOL_SIZE));
        config.setMinimumIdle(getIntProperty(clientProperties, "ojp.connection.pool.minimumIdle", CommonConstants.DEFAULT_MINIMUM_IDLE));
        config.setIdleTimeout(getLongProperty(clientProperties, "ojp.connection.pool.idleTimeout", CommonConstants.DEFAULT_IDLE_TIMEOUT));
        config.setMaxLifetime(getLongProperty(clientProperties, "ojp.connection.pool.maxLifetime", CommonConstants.DEFAULT_MAX_LIFETIME));
        config.setConnectionTimeout(getLongProperty(clientProperties, "ojp.connection.pool.connectionTimeout", CommonConstants.DEFAULT_CONNECTION_TIMEOUT));

        log.info("HikariCP configured with maximumPoolSize={}, minimumIdle={}, poolName={}",
                config.getMaximumPoolSize(), config.getMinimumIdle(), config.getPoolName());
    }

    /**
     * Extracts client properties from connection details.
     *
     * @param connectionDetails The connection details
     * @return Properties object or null if not available
     */
    private static Properties extractClientProperties(ConnectionDetails connectionDetails) {
        if (connectionDetails.getProperties().isEmpty()) {
            return null;
        }

        try {
            Properties clientProperties = deserialize(connectionDetails.getProperties().toByteArray(), Properties.class);
            log.info("Received {} properties from client for connection pool configuration", clientProperties.size());
            return clientProperties;
        } catch (Exception e) {
            log.warn("Failed to deserialize client properties, using defaults: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets an integer property with a default value.
     *
     * @param properties   The properties object
     * @param key         The property key
     * @param defaultValue The default value
     * @return The property value or default
     */
    private static int getIntProperty(Properties properties, String key, int defaultValue) {
        if (properties == null || !properties.containsKey(key)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(properties.getProperty(key));
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for property '{}': {}, using default: {}", key, properties.getProperty(key), defaultValue);
            return defaultValue;
        }
    }

    /**
     * Gets a long property with a default value.
     *
     * @param properties   The properties object
     * @param key         The property key
     * @param defaultValue The default value
     * @return The property value or default
     */
    private static long getLongProperty(Properties properties, String key, long defaultValue) {
        if (properties == null || !properties.containsKey(key)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(properties.getProperty(key));
        } catch (NumberFormatException e) {
            log.warn("Invalid long value for property '{}': {}, using default: {}", key, properties.getProperty(key), defaultValue);
            return defaultValue;
        }
    }
}