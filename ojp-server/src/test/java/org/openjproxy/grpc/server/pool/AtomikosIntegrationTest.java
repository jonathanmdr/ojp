package org.openjproxy.grpc.server.pool;

import com.atomikos.jdbc.AtomikosDataSourceBean;
import com.google.protobuf.ByteString;
import com.openjproxy.grpc.ConnectionDetails;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openjproxy.constants.CommonConstants;
import org.openjproxy.grpc.SerializationHandler;

import javax.sql.XADataSource;
import java.sql.Connection;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Atomikos XA transaction support.
 */
public class AtomikosIntegrationTest {

    @BeforeAll
    public static void setup() {
        // Start Atomikos with logging disabled for tests
        if (!AtomikosLifecycle.isStarted()) {
            AtomikosLifecycle.start(false, System.getProperty("java.io.tmpdir"));
        }
    }

    @AfterAll
    public static void teardown() {
        // Stop Atomikos after tests
        if (AtomikosLifecycle.isStarted()) {
            AtomikosLifecycle.stop();
        }
    }

    @Test
    public void testAtomikosLifecycleStartStop() {
        // Verify Atomikos is started
        assertTrue(AtomikosLifecycle.isStarted(), "Atomikos should be started");
        assertTrue(AtomikosLifecycle.isInitialized(), "Atomikos should be initialized");
        assertNotNull(AtomikosLifecycle.getUserTransactionService(), "UserTransactionService should not be null");
    }

    @Test
    public void testAtomikosDataSourceCreation() throws Exception {
        // Create test properties
        Properties clientProperties = new Properties();
        clientProperties.setProperty(CommonConstants.MAXIMUM_POOL_SIZE_PROPERTY, "10");
        clientProperties.setProperty(CommonConstants.MINIMUM_IDLE_PROPERTY, "2");
        clientProperties.setProperty(CommonConstants.CONNECTION_TIMEOUT_PROPERTY, "5000");
        clientProperties.setProperty(CommonConstants.IDLE_TIMEOUT_PROPERTY, "30000");
        clientProperties.setProperty(CommonConstants.MAX_LIFETIME_PROPERTY, "60000");

        // Serialize properties
        byte[] serializedProperties = SerializationHandler.serialize(clientProperties);

        // Create ConnectionDetails
        ConnectionDetails connectionDetails = ConnectionDetails.newBuilder()
                .setUrl("jdbc:h2:mem:testxa")
                .setUser("test")
                .setPassword("test")
                .setClientUUID("test-client")
                .setIsXA(true)
                .setProperties(ByteString.copyFrom(serializedProperties))
                .build();

        // Create a mock XADataSource (H2 supports XA)
        org.h2.jdbcx.JdbcDataSource xaDataSource = new org.h2.jdbcx.JdbcDataSource();
        xaDataSource.setURL("jdbc:h2:mem:testxa");
        xaDataSource.setUser("test");
        xaDataSource.setPassword("test");

        // Create Atomikos DataSource
        String uniqueResourceName = "TEST_XA_DS";
        AtomikosDataSourceBean atomikosDS = AtomikosDataSourceFactory.createAtomikosDataSource(
                xaDataSource, connectionDetails, uniqueResourceName);

        // Verify configuration
        assertNotNull(atomikosDS, "AtomikosDataSourceBean should not be null");
        assertEquals(10, atomikosDS.getMaxPoolSize(), "Max pool size should be 10");
        assertEquals(2, atomikosDS.getMinPoolSize(), "Min pool size should be 2");
        assertEquals(5, atomikosDS.getBorrowConnectionTimeout(), "Borrow timeout should be 5 seconds");
        assertEquals(30, atomikosDS.getMaxIdleTime(), "Max idle time should be 30 seconds");
        assertEquals(60, atomikosDS.getReapTimeout(), "Reap timeout should be 60 seconds");
        assertEquals(uniqueResourceName, atomikosDS.getUniqueResourceName(), "Resource name should match");

        // Test connection acquisition
        Connection conn = null;
        try {
            conn = atomikosDS.getConnection();
            assertNotNull(conn, "Connection should not be null");
            assertFalse(conn.isClosed(), "Connection should not be closed");
        } finally {
            if (conn != null) {
                conn.close();
            }
            atomikosDS.close();
        }
    }

    @Test
    public void testMillisecondsToSecondsConversion() throws Exception {
        // Test that milliseconds are correctly converted to seconds with minimum of 1
        Properties clientProperties = new Properties();
        clientProperties.setProperty(CommonConstants.CONNECTION_TIMEOUT_PROPERTY, "500"); // 500ms -> 1 second (minimum)
        
        byte[] serializedProperties = SerializationHandler.serialize(clientProperties);
        
        ConnectionDetails connectionDetails = ConnectionDetails.newBuilder()
                .setUrl("jdbc:h2:mem:testconversion")
                .setUser("test")
                .setPassword("test")
                .setIsXA(true)
                .setProperties(ByteString.copyFrom(serializedProperties))
                .build();

        org.h2.jdbcx.JdbcDataSource xaDataSource = new org.h2.jdbcx.JdbcDataSource();
        xaDataSource.setURL("jdbc:h2:mem:testconversion");

        AtomikosDataSourceBean atomikosDS = AtomikosDataSourceFactory.createAtomikosDataSource(
                xaDataSource, connectionDetails, "TEST_CONVERSION_DS");

        // Verify minimum of 1 second is enforced
        assertEquals(1, atomikosDS.getBorrowConnectionTimeout(), 
                "Timeout should be at least 1 second even for values less than 1000ms");
        
        atomikosDS.close();
    }

    @Test
    public void testDefaultValues() throws Exception {
        // Test with no properties (should use defaults)
        ConnectionDetails connectionDetails = ConnectionDetails.newBuilder()
                .setUrl("jdbc:h2:mem:testdefaults")
                .setUser("test")
                .setPassword("test")
                .setIsXA(true)
                .build();

        org.h2.jdbcx.JdbcDataSource xaDataSource = new org.h2.jdbcx.JdbcDataSource();
        xaDataSource.setURL("jdbc:h2:mem:testdefaults");

        AtomikosDataSourceBean atomikosDS = AtomikosDataSourceFactory.createAtomikosDataSource(
                xaDataSource, connectionDetails, "TEST_DEFAULTS_DS");

        // Verify default values from CommonConstants
        assertEquals(CommonConstants.DEFAULT_MAXIMUM_POOL_SIZE, atomikosDS.getMaxPoolSize(), 
                "Max pool size should use default");
        assertEquals(CommonConstants.DEFAULT_MINIMUM_IDLE, atomikosDS.getMinPoolSize(), 
                "Min pool size should use default");
        
        atomikosDS.close();
    }

    @Test
    public void testDataSourceConfigurationManagerCaching() throws Exception {
        // Create same properties twice
        Properties props1 = new Properties();
        props1.setProperty(CommonConstants.MAXIMUM_POOL_SIZE_PROPERTY, "15");
        props1.setProperty(CommonConstants.DATASOURCE_NAME_PROPERTY, "test-ds");
        
        Properties props2 = new Properties();
        props2.setProperty(CommonConstants.MAXIMUM_POOL_SIZE_PROPERTY, "15");
        props2.setProperty(CommonConstants.DATASOURCE_NAME_PROPERTY, "test-ds");
        
        // Get configuration twice
        DataSourceConfigurationManager.DataSourceConfiguration config1 = 
                DataSourceConfigurationManager.getConfiguration(props1);
        DataSourceConfigurationManager.DataSourceConfiguration config2 = 
                DataSourceConfigurationManager.getConfiguration(props2);
        
        // Verify caching (should return same instance)
        assertSame(config1, config2, "Configurations with same properties should be cached");
        
        // Clear cache and verify
        DataSourceConfigurationManager.clearCache();
        DataSourceConfigurationManager.DataSourceConfiguration config3 = 
                DataSourceConfigurationManager.getConfiguration(props1);
        assertNotSame(config1, config3, "After clearing cache, new instance should be created");
    }
}
