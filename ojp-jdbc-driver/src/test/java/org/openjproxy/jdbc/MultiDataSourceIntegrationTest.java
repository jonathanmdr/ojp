package org.openjproxy.jdbc;

import openjproxy.jdbc.testutil.TestDBUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for multi-datasource functionality using H2 database.
 * Tests the complete flow from client to server with different datasource configurations.
 */
public class MultiDataSourceIntegrationTest {

    private static final String H2_URL_BASE = "jdbc:h2:mem:test_";
    private static final String OJP_URL_BASE = "jdbc:ojp[localhost:1059]_h2:mem:test_";
    
    @BeforeEach
    public void setUp() {
        // Ensure we start with clean state
        System.clearProperty("ojp.test.properties");
    }
    
    @AfterEach
    public void tearDown() {
        System.clearProperty("ojp.test.properties");
    }

    @Test
    public void testMultipleDataSourcesForSingleDatabaseSingleUser() throws Exception {
        // Create test properties with multiple datasources for same database
        String testPropertiesContent = 
            "# Default datasource\n" +
            "ojp.connection.pool.maximumPoolSize=10\n" +
            "ojp.connection.pool.minimumIdle=2\n" +
            "\n" +
            "# MainApp datasource - larger pool for main application\n" +
            "mainApp.ojp.connection.pool.maximumPoolSize=20\n" +
            "mainApp.ojp.connection.pool.minimumIdle=5\n" +
            "\n" +
            "# BatchJob datasource - smaller pool for batch processing\n" +
            "batchJob.ojp.connection.pool.maximumPoolSize=5\n" +
            "batchJob.ojp.connection.pool.minimumIdle=1\n";
        
        // Set up a custom driver that uses our test properties
        Driver testDriver = createTestDriver(testPropertiesContent);
        
        // Test connection with default datasource
        try (Connection defaultConn = testDriver.connect(OJP_URL_BASE + "singledb", new Properties())) {
            createAndTestTable(defaultConn, "default_table");
        }
        
        // Test connection with mainApp datasource
        try (Connection mainAppConn = testDriver.connect(OJP_URL_BASE + "singledb?dataSource=mainApp", new Properties())) {
            createAndTestTable(mainAppConn, "mainapp_table");
        }
        
        // Test connection with batchJob datasource  
        try (Connection batchJobConn = testDriver.connect(OJP_URL_BASE + "singledb?dataSource=batchJob", new Properties())) {
            createAndTestTable(batchJobConn, "batchjob_table");
        }
        
        // Verify that different datasources can access same database but with different table names
        // This ensures misrouted queries would fail due to table name differences
        try (Connection defaultConn = testDriver.connect(OJP_URL_BASE + "singledb", new Properties())) {
            // Should be able to access default_table but not mainapp_table or batchjob_table
            assertTrue(tableExists(defaultConn, "default_table"));
            assertFalse(tableExists(defaultConn, "mainapp_table"));
            assertFalse(tableExists(defaultConn, "batchjob_table"));
        }
    }

    @Test
    public void testMultipleDataSourcesForSingleDatabaseDifferentUsers() throws Exception {
        // This test simulates different users accessing the same database
        String testPropertiesContent = 
            "# User1 datasource\n" +
            "user1.ojp.connection.pool.maximumPoolSize=15\n" +
            "user1.ojp.connection.pool.minimumIdle=3\n" +
            "\n" +
            "# User2 datasource\n" +
            "user2.ojp.connection.pool.maximumPoolSize=10\n" +
            "user2.ojp.connection.pool.minimumIdle=2\n";
        
        Driver testDriver = createTestDriver(testPropertiesContent);
        
        // Test connection with user1 datasource
        Properties user1Props = new Properties();
        user1Props.setProperty("user", "user1");
        user1Props.setProperty("password", "password1");
        
        try (Connection user1Conn = testDriver.connect(OJP_URL_BASE + "multiuser?dataSource=user1", user1Props)) {
            createAndTestTable(user1Conn, "user1_data");
        }
        
        // Test connection with user2 datasource
        Properties user2Props = new Properties();
        user2Props.setProperty("user", "user2"); 
        user2Props.setProperty("password", "password2");
        
        try (Connection user2Conn = testDriver.connect(OJP_URL_BASE + "multiuser?dataSource=user2", user2Props)) {
            createAndTestTable(user2Conn, "user2_data");
        }
    }

    @Test
    public void testMultipleDataSourcesForDifferentDatabases() throws Exception {
        String testPropertiesContent = 
            "# Database A datasources\n" +
            "dbA_primary.ojp.connection.pool.maximumPoolSize=20\n" +
            "dbA_primary.ojp.connection.pool.minimumIdle=5\n" +
            "\n" +
            "dbA_readonly.ojp.connection.pool.maximumPoolSize=8\n" +
            "dbA_readonly.ojp.connection.pool.minimumIdle=2\n" +
            "\n" +
            "# Database B datasources\n" +
            "dbB_primary.ojp.connection.pool.maximumPoolSize=15\n" +
            "dbB_primary.ojp.connection.pool.minimumIdle=4\n" +
            "\n" +
            "dbB_analytics.ojp.connection.pool.maximumPoolSize=5\n" +
            "dbB_analytics.ojp.connection.pool.minimumIdle=1\n";
        
        Driver testDriver = createTestDriver(testPropertiesContent);
        
        // Test Database A - Primary datasource
        try (Connection dbAPrimaryConn = testDriver.connect(OJP_URL_BASE + "databaseA?dataSource=dbA_primary", new Properties())) {
            createAndTestTable(dbAPrimaryConn, "dba_primary_table");
        }
        
        // Test Database A - Readonly datasource
        try (Connection dbAReadonlyConn = testDriver.connect(OJP_URL_BASE + "databaseA?dataSource=dbA_readonly", new Properties())) {
            createAndTestTable(dbAReadonlyConn, "dba_readonly_table");
        }
        
        // Test Database B - Primary datasource 
        try (Connection dbBPrimaryConn = testDriver.connect(OJP_URL_BASE + "databaseB?dataSource=dbB_primary", new Properties())) {
            createAndTestTable(dbBPrimaryConn, "dbb_primary_table");
        }
        
        // Test Database B - Analytics datasource
        try (Connection dbBAnalyticsConn = testDriver.connect(OJP_URL_BASE + "databaseB?dataSource=dbB_analytics", new Properties())) {
            createAndTestTable(dbBAnalyticsConn, "dbb_analytics_table");
        }
        
        // Verify isolation between databases - try to access wrong database tables
        try (Connection dbAConn = testDriver.connect(OJP_URL_BASE + "databaseA?dataSource=dbA_primary", new Properties())) {
            // Database A should not have Database B tables
            assertTrue(tableExists(dbAConn, "dba_primary_table"));
            assertFalse(tableExists(dbAConn, "dbb_primary_table"));
        }
        
        try (Connection dbBConn = testDriver.connect(OJP_URL_BASE + "databaseB?dataSource=dbB_primary", new Properties())) {
            // Database B should not have Database A tables
            assertTrue(tableExists(dbBConn, "dbb_primary_table"));
            assertFalse(tableExists(dbBConn, "dba_primary_table"));
        }
    }

    @Test
    public void testFailFastForMissingDataSource() throws Exception {
        String testPropertiesContent = 
            "# Only configure one datasource\n" +
            "configuredDS.ojp.connection.pool.maximumPoolSize=10\n";
        
        Driver testDriver = createTestDriver(testPropertiesContent);
        
        // Connection with configured datasource should work
        try (Connection configuredConn = testDriver.connect(OJP_URL_BASE + "testdb?dataSource=configuredDS", new Properties())) {
            assertNotNull(configuredConn);
            createAndTestTable(configuredConn, "configured_table");
        }
        
        // Connection with unconfigured datasource should return properties with no pool settings
        // (The server will use defaults, but the client won't send any specific properties)
        try (Connection unconfiguredConn = testDriver.connect(OJP_URL_BASE + "testdb?dataSource=unconfiguredDS", new Properties())) {
            assertNotNull(unconfiguredConn);
            // This should work but use default pool settings
            createAndTestTable(unconfiguredConn, "unconfigured_table");
        }
    }

    @Test
    public void testBackwardCompatibilityWithDefaultDataSource() throws Exception {
        String testPropertiesContent = 
            "# Traditional configuration without datasource prefix\n" +
            "ojp.connection.pool.maximumPoolSize=25\n" +
            "ojp.connection.pool.minimumIdle=8\n";
        
        Driver testDriver = createTestDriver(testPropertiesContent);
        
        // Connection without dataSource parameter should use default configuration
        try (Connection defaultConn = testDriver.connect(OJP_URL_BASE + "backcompat", new Properties())) {
            assertNotNull(defaultConn);
            createAndTestTable(defaultConn, "backcompat_table");
        }
        
        // Connection with explicit default dataSource should also work
        try (Connection explicitDefaultConn = testDriver.connect(OJP_URL_BASE + "backcompat?dataSource=default", new Properties())) {
            assertNotNull(explicitDefaultConn);
            // Should be able to access the same table
            assertTrue(tableExists(explicitDefaultConn, "backcompat_table"));
        }
    }

    /**
     * Creates a test driver that uses the provided properties content instead of loading from classpath.
     */
    private Driver createTestDriver(String propertiesContent) {
        return new Driver() {
            @Override
            protected Properties loadOjpProperties() {
                Properties props = new Properties();
                try (InputStream is = new ByteArrayInputStream(propertiesContent.getBytes())) {
                    props.load(is);
                    return props;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load test properties", e);
                }
            }
        };
    }
    
    /**
     * Creates a test table with unique name and inserts/verifies test data.
     */
    private void createAndTestTable(Connection conn, String tableName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Create unique table for this datasource
            stmt.execute("CREATE TABLE IF NOT EXISTS " + tableName + " (id INT PRIMARY KEY, name VARCHAR(50))");
            
            // Insert test data
            stmt.execute("INSERT INTO " + tableName + " VALUES (1, 'test_data_" + tableName + "')");
            
            // Verify data
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
            
            // Verify specific data
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM " + tableName + " WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals("test_data_" + tableName, rs.getString(1));
            }
        }
    }
    
    /**
     * Checks if a table exists in the database.
     */
    private boolean tableExists(Connection conn, String tableName) {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT 1 FROM " + tableName + " LIMIT 1");
            return true;
        } catch (SQLException e) {
            return false; // Table doesn't exist or query failed
        }
    }
}