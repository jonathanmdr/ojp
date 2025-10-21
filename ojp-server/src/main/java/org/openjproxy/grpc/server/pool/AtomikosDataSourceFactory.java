package org.openjproxy.grpc.server.pool;

import com.atomikos.jdbc.AtomikosDataSourceBean;
import com.openjproxy.grpc.ConnectionDetails;
import lombok.extern.slf4j.Slf4j;
import org.openjproxy.constants.CommonConstants;

import javax.sql.XADataSource;
import java.util.Properties;

import static org.openjproxy.grpc.SerializationHandler.deserialize;

/**
 * Factory for creating and configuring AtomikosDataSourceBean instances for XA transactions.
 * Maps Hikari configuration keys to Atomikos properties and handles milliseconds to seconds conversion.
 */
@Slf4j
public class AtomikosDataSourceFactory {

    /**
     * Creates and configures an AtomikosDataSourceBean from an XADataSource and connection details.
     * 
     * @param xaDataSource The underlying XADataSource
     * @param connectionDetails Connection details containing configuration properties
     * @param uniqueResourceName A unique name for this XA resource
     * @return Configured AtomikosDataSourceBean
     */
    public static AtomikosDataSourceBean createAtomikosDataSource(
            XADataSource xaDataSource, 
            ConnectionDetails connectionDetails,
            String uniqueResourceName) {
        
        Properties clientProperties = extractClientProperties(connectionDetails);
        
        // Get datasource-specific configuration using existing infrastructure
        DataSourceConfigurationManager.DataSourceConfiguration dsConfig = 
                DataSourceConfigurationManager.getConfiguration(clientProperties);
        
        // Create Atomikos datasource
        AtomikosDataSourceBean atomikosDS = new AtomikosDataSourceBean();
        
        // Set the underlying XADataSource
        atomikosDS.setXaDataSource(xaDataSource);
        
        // Set unique resource name (required by Atomikos)
        atomikosDS.setUniqueResourceName(uniqueResourceName);
        
        // Map HikariCP configuration to Atomikos properties
        // maximumPoolSize -> setMaxPoolSize
        atomikosDS.setMaxPoolSize(dsConfig.getMaximumPoolSize());
        
        // minimumIdle -> setMinPoolSize
        atomikosDS.setMinPoolSize(dsConfig.getMinimumIdle());
        
        // connectionTimeoutMs -> setBorrowConnectionTimeout (convert ms to seconds)
        int borrowTimeoutSeconds = millisecondsToSeconds(dsConfig.getConnectionTimeout());
        atomikosDS.setBorrowConnectionTimeout(borrowTimeoutSeconds);
        
        // idleTimeoutMs -> setMaxIdleTime (convert ms to seconds)
        int maxIdleTimeSeconds = millisecondsToSeconds(dsConfig.getIdleTimeout());
        atomikosDS.setMaxIdleTime(maxIdleTimeSeconds);
        
        // maxLifetimeMs -> setReapTimeout (convert ms to seconds)
        // Note: reapTimeout is for connection reaping/cleanup interval, closest match to maxLifetime
        int reapTimeoutSeconds = millisecondsToSeconds(dsConfig.getMaxLifetime());
        atomikosDS.setReapTimeout(reapTimeoutSeconds);
        
        // Additional Atomikos-specific settings for optimal performance
        atomikosDS.setTestQuery("SELECT 1"); // Test query for connection validation
        atomikosDS.setMaintenanceInterval(60); // Maintenance interval in seconds
        
        log.info("Created AtomikosDataSourceBean '{}' for dataSource '{}' with maxPoolSize={}, minPoolSize={}, borrowTimeout={}s",
                uniqueResourceName, dsConfig.getDataSourceName(), 
                atomikosDS.getMaxPoolSize(), atomikosDS.getMinPoolSize(), borrowTimeoutSeconds);
        
        return atomikosDS;
    }
    
    /**
     * Converts milliseconds to seconds, ensuring at least 1 second.
     * 
     * @param milliseconds Time in milliseconds
     * @return Time in seconds (minimum 1)
     */
    private static int millisecondsToSeconds(long milliseconds) {
        return Math.max(1, Math.toIntExact(Math.round(milliseconds / 1000.0)));
    }
    
    /**
     * Extracts client properties from connection details.
     */
    private static Properties extractClientProperties(ConnectionDetails connectionDetails) {
        if (connectionDetails.getProperties().isEmpty()) {
            return new Properties();
        }
        
        try {
            Properties clientProperties = deserialize(connectionDetails.getProperties().toByteArray(), Properties.class);
            log.debug("Extracted {} properties from client for Atomikos configuration", clientProperties.size());
            return clientProperties;
        } catch (Exception e) {
            log.warn("Failed to deserialize client properties, using defaults: {}", e.getMessage());
            return new Properties();
        }
    }
}
