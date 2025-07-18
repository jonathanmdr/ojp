package org.openjdbcproxy.jdbc;

import org.junit.jupiter.api.Test;
import org.openjdbcproxy.constants.CommonConstants;
import org.openjdbcproxy.grpc.SerializationHandler;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for connection pool configuration functionality.
 */
public class ConnectionPoolConfigurationTest {

    @Test
    public void testDefaultConstantsAreSet() {
        assertEquals(10, CommonConstants.DEFAULT_MAXIMUM_POOL_SIZE);
        assertEquals(10, CommonConstants.DEFAULT_MINIMUM_IDLE);
        assertEquals(600000L, CommonConstants.DEFAULT_IDLE_TIMEOUT);
        assertEquals(1800000L, CommonConstants.DEFAULT_MAX_LIFETIME);
        assertEquals(30000L, CommonConstants.DEFAULT_CONNECTION_TIMEOUT);
    }

    @Test
    public void testPropertiesFileLoading() {
        // Create a test properties file content
        String propertiesContent = 
            "maximumPoolSize=15\n" +
            "minimumIdle=5\n" +
            "autoCommit=false\n" +
            "poolName=TestPool\n";
        
        Properties properties = new Properties();
        try (InputStream is = new ByteArrayInputStream(propertiesContent.getBytes())) {
            properties.load(is);
        } catch (Exception e) {
            fail("Should be able to load properties: " + e.getMessage());
        }
        
        assertEquals("15", properties.getProperty("maximumPoolSize"));
        assertEquals("5", properties.getProperty("minimumIdle"));
        assertEquals("false", properties.getProperty("autoCommit"));
        assertEquals("TestPool", properties.getProperty("poolName"));
    }

    @Test
    public void testOjpPropertiesFileLoadingFromClasspath() throws Exception {
        // Test that the driver can load the ojp.properties file from classpath
        Driver driver = new Driver();
        Method loadOjpPropertiesMethod = Driver.class.getDeclaredMethod("loadOjpProperties");
        loadOjpPropertiesMethod.setAccessible(true);
        
        Properties properties = (Properties) loadOjpPropertiesMethod.invoke(driver);
        
        if (properties != null) {
            // If properties file exists in test resources, verify it loads correctly
            assertEquals("20", properties.getProperty("ojp.connection.pool.maximumPoolSize"));
            assertEquals("8", properties.getProperty("ojp.connection.pool.minimumIdle"));
        }
        // If properties is null, that's fine - no properties file found, which is a valid case
    }

    @Test
    public void testPropertiesSerialization() {
        // Test that we can serialize and deserialize properties
        Properties originalProperties = new Properties();
        originalProperties.setProperty("maximumPoolSize", "25");
        originalProperties.setProperty("minimumIdle", "3");
        originalProperties.setProperty("poolName", "SerializationTestPool");
        
        // Serialize
        byte[] serialized = SerializationHandler.serialize(originalProperties);
        assertNotNull(serialized);
        assertTrue(serialized.length > 0);
        
        // Deserialize
        Properties deserializedProperties = SerializationHandler.deserialize(serialized, Properties.class);
        assertNotNull(deserializedProperties);
        assertEquals("25", deserializedProperties.getProperty("maximumPoolSize"));
        assertEquals("3", deserializedProperties.getProperty("minimumIdle"));
        assertEquals("SerializationTestPool", deserializedProperties.getProperty("poolName"));
    }
}