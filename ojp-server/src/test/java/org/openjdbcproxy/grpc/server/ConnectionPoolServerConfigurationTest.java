package org.openjdbcproxy.grpc.server;

import com.google.protobuf.ByteString;
import com.openjdbcproxy.grpc.ConnectionDetails;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;
import org.openjdbcproxy.constants.CommonConstants;
import org.openjdbcproxy.grpc.SerializationHandler;

import java.lang.reflect.Method;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for server-side connection pool configuration.
 */
public class ConnectionPoolServerConfigurationTest {

    @Test
    public void testHikariConfigurationWithClientProperties() throws Exception {
        // Create a StatementServiceImpl instance
        StatementServiceImpl serviceImpl = new StatementServiceImpl(null, null);
        
        // Create test properties that a client would send
        Properties clientProperties = new Properties();
        clientProperties.setProperty("maximumPoolSize", "25");
        clientProperties.setProperty("minimumIdle", "7");
        clientProperties.setProperty("autoCommit", "false");
        clientProperties.setProperty("poolName", "TestIntegrationPool");
        clientProperties.setProperty("validationTimeout", "8000");
        
        // Serialize properties as the client would
        byte[] serializedProperties = SerializationHandler.serialize(clientProperties);
        
        // Create ConnectionDetails with properties
        ConnectionDetails connectionDetails = ConnectionDetails.newBuilder()
                .setUrl("jdbc:h2:mem:testdb")
                .setUser("test")
                .setPassword("test")
                .setClientUUID("test-client")
                .setProperties(ByteString.copyFrom(serializedProperties))
                .build();
        
        // Create HikariConfig to test configuration
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:testdb");
        config.setUsername("test");
        config.setPassword("test");
        
        // Use reflection to call the private configureHikariPool method
        Method configureMethod = StatementServiceImpl.class.getDeclaredMethod("configureHikariPool", HikariConfig.class, ConnectionDetails.class);
        configureMethod.setAccessible(true);
        configureMethod.invoke(serviceImpl, config, connectionDetails);
        
        // Verify that client properties were applied
        assertEquals(25, config.getMaximumPoolSize());
        assertEquals(7, config.getMinimumIdle());
        assertEquals(false, config.isAutoCommit());
        assertEquals("TestIntegrationPool", config.getPoolName());
        assertEquals(8000, config.getValidationTimeout());
        
        // Verify that properties not provided use defaults
        assertEquals(CommonConstants.DEFAULT_IDLE_TIMEOUT, config.getIdleTimeout());
        assertEquals(CommonConstants.DEFAULT_MAX_LIFETIME, config.getMaxLifetime());
        assertEquals(CommonConstants.DEFAULT_CONNECTION_TIMEOUT, config.getConnectionTimeout());
    }

    @Test
    public void testHikariConfigurationWithoutClientProperties() throws Exception {
        // Create a StatementServiceImpl instance
        StatementServiceImpl serviceImpl = new StatementServiceImpl(null, null);
        
        // Create ConnectionDetails without properties
        ConnectionDetails connectionDetails = ConnectionDetails.newBuilder()
                .setUrl("jdbc:h2:mem:testdb")
                .setUser("test")
                .setPassword("test")
                .setClientUUID("test-client")
                .build();
        
        // Create HikariConfig to test configuration
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:testdb");
        config.setUsername("test");
        config.setPassword("test");
        
        // Use reflection to call the private configureHikariPool method
        Method configureMethod = StatementServiceImpl.class.getDeclaredMethod("configureHikariPool", HikariConfig.class, ConnectionDetails.class);
        configureMethod.setAccessible(true);
        configureMethod.invoke(serviceImpl, config, connectionDetails);
        
        // Verify that all default values are applied
        assertEquals(CommonConstants.DEFAULT_MAXIMUM_POOL_SIZE, config.getMaximumPoolSize());
        assertEquals(CommonConstants.DEFAULT_MINIMUM_IDLE, config.getMinimumIdle());
        assertEquals(CommonConstants.DEFAULT_AUTO_COMMIT, config.isAutoCommit());
        assertEquals(CommonConstants.DEFAULT_POOL_NAME, config.getPoolName());
        assertEquals(CommonConstants.DEFAULT_VALIDATION_TIMEOUT, config.getValidationTimeout());
        assertEquals(CommonConstants.DEFAULT_IDLE_TIMEOUT, config.getIdleTimeout());
        assertEquals(CommonConstants.DEFAULT_MAX_LIFETIME, config.getMaxLifetime());
        assertEquals(CommonConstants.DEFAULT_CONNECTION_TIMEOUT, config.getConnectionTimeout());
        assertEquals(CommonConstants.DEFAULT_LEAK_DETECTION_THRESHOLD, config.getLeakDetectionThreshold());
        assertEquals(CommonConstants.DEFAULT_ISOLATE_INTERNAL_QUERIES, config.isIsolateInternalQueries());
        assertEquals(CommonConstants.DEFAULT_ALLOW_POOL_SUSPENSION, config.isAllowPoolSuspension());
    }

    @Test
    public void testHikariConfigurationWithInvalidProperties() throws Exception {
        // Create a StatementServiceImpl instance
        StatementServiceImpl serviceImpl = new StatementServiceImpl(null, null);
        
        // Create test properties with invalid values
        Properties clientProperties = new Properties();
        clientProperties.setProperty("maximumPoolSize", "invalid_number");
        clientProperties.setProperty("minimumIdle", "not_a_number");
        clientProperties.setProperty("autoCommit", "true"); // This one is valid
        clientProperties.setProperty("poolName", "ValidPoolName"); // This one is valid
        
        // Serialize properties as the client would
        byte[] serializedProperties = SerializationHandler.serialize(clientProperties);
        
        // Create ConnectionDetails with properties
        ConnectionDetails connectionDetails = ConnectionDetails.newBuilder()
                .setUrl("jdbc:h2:mem:testdb")
                .setUser("test")
                .setPassword("test")
                .setClientUUID("test-client")
                .setProperties(ByteString.copyFrom(serializedProperties))
                .build();
        
        // Create HikariConfig to test configuration
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:testdb");
        config.setUsername("test");
        config.setPassword("test");
        
        // Use reflection to call the private configureHikariPool method
        Method configureMethod = StatementServiceImpl.class.getDeclaredMethod("configureHikariPool", HikariConfig.class, ConnectionDetails.class);
        configureMethod.setAccessible(true);
        configureMethod.invoke(serviceImpl, config, connectionDetails);
        
        // Verify that invalid values fall back to defaults, but valid values are applied
        assertEquals(CommonConstants.DEFAULT_MAXIMUM_POOL_SIZE, config.getMaximumPoolSize()); // Falls back to default
        assertEquals(CommonConstants.DEFAULT_MINIMUM_IDLE, config.getMinimumIdle()); // Falls back to default
        assertEquals(true, config.isAutoCommit()); // Valid value applied
        assertEquals("ValidPoolName", config.getPoolName()); // Valid value applied
    }
}