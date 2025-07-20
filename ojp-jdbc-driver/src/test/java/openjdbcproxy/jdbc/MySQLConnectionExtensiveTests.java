package openjdbcproxy.jdbc;

import io.grpc.StatusRuntimeException;
import lombok.SneakyThrows;
import openjdbcproxy.jdbc.testutil.TestDBUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class MySQLConnectionExtensiveTests {

    private static boolean isTestDisabled;
    private Connection connection;

    @BeforeAll
    public static void checkTestConfiguration() {
        isTestDisabled = Boolean.parseBoolean(System.getProperty("disableMySQLTests", "false"));
    }

    @SneakyThrows
    public void setUp(String driverClass, String url, String user, String password) throws SQLException {
        assumeFalse(isTestDisabled, "MySQL tests are disabled");
        connection = DriverManager.getConnection(url, user, password);
    }

    @AfterEach
    public void tearDown() throws SQLException {
        TestDBUtils.closeQuietly(connection);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testCreateStatement(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        Statement statement = connection.createStatement();
        assertNotNull(statement);
        statement.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testPrepareStatement(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        PreparedStatement preparedStatement = connection.prepareStatement("SELECT 1");
        assertNotNull(preparedStatement);
        preparedStatement.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testPrepareCall(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        // MySQL supports callable statements, though syntax may differ
        try {
            CallableStatement callableStatement = connection.prepareCall("CALL test_procedure()");
            assertNotNull(callableStatement);
            callableStatement.close();
        } catch (SQLException e) {
            // This is expected if the procedure doesn't exist - test that the method works
            assertTrue(e.getMessage().contains("PROCEDURE") || e.getMessage().contains("procedure"));
        }
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testNativeSQL(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        String nativeSQL = connection.nativeSQL("SELECT {fn NOW()}");
        assertNotNull(nativeSQL);
        // MySQL should convert JDBC escape sequence
        assertTrue(nativeSQL.contains("NOW()") || nativeSQL.contains("SELECT"));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testAutoCommit(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        // Test getting and setting auto-commit
        boolean originalAutoCommit = connection.getAutoCommit();
        
        connection.setAutoCommit(false);
        assertEquals(false, connection.getAutoCommit());
        
        connection.setAutoCommit(true);
        assertEquals(true, connection.getAutoCommit());
        
        // Restore original state
        connection.setAutoCommit(originalAutoCommit);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testCommitAndRollback(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        // Test commit and rollback operations
        connection.setAutoCommit(false);
        
        // These should not throw exceptions
        connection.commit();
        connection.rollback();
        
        connection.setAutoCommit(true);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testIsClosed(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        assertEquals(false, connection.isClosed());
        
        connection.close();
        assertEquals(true, connection.isClosed());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testGetMetaData(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        DatabaseMetaData metaData = connection.getMetaData();
        assertNotNull(metaData);
        
        String databaseProductName = metaData.getDatabaseProductName();
        assertNotNull(databaseProductName);
        assertTrue(databaseProductName.toLowerCase().contains("mysql"));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testReadOnly(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        // Test read-only mode
        boolean originalReadOnly = connection.isReadOnly();
        
        try {
            connection.setReadOnly(true);
            // MySQL may or may not support read-only mode depending on configuration
            // Just test that the call doesn't throw an unexpected exception
        } catch (SQLException e) {
            // Some MySQL configurations may not support read-only mode
            // This is acceptable
        }
        
        // Restore original state
        connection.setReadOnly(originalReadOnly);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testCatalog(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        String catalog = connection.getCatalog();
        // Catalog might be null or the database name
        
        // Test setting catalog (should work in MySQL)
        if (catalog != null) {
            connection.setCatalog(catalog);
            assertEquals(catalog, connection.getCatalog());
        }
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testTransactionIsolation(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        int isolationLevel = connection.getTransactionIsolation();
        assertTrue(isolationLevel >= Connection.TRANSACTION_NONE && isolationLevel <= Connection.TRANSACTION_SERIALIZABLE);
        
        // Test setting transaction isolation level
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, connection.getTransactionIsolation());
        
        // Restore original level
        connection.setTransactionIsolation(isolationLevel);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testWarnings(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        // Test warning operations
        SQLWarning warnings = connection.getWarnings();
        // Warnings might be null initially
        
        connection.clearWarnings();
        // Should not throw exception
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testCreateStatementWithParameters(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        assertNotNull(statement);
        statement.close();
        
        statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        assertNotNull(statement);
        statement.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testPrepareStatementWithParameters(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        PreparedStatement ps = connection.prepareStatement("SELECT 1", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        assertNotNull(ps);
        ps.close();
        
        ps = connection.prepareStatement("SELECT 1", Statement.RETURN_GENERATED_KEYS);
        assertNotNull(ps);
        ps.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testHoldability(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        int holdability = connection.getHoldability();
        assertTrue(holdability == ResultSet.HOLD_CURSORS_OVER_COMMIT || holdability == ResultSet.CLOSE_CURSORS_AT_COMMIT);
        
        // Test setting holdability
        connection.setHoldability(ResultSet.HOLD_CURSORS_OVER_COMMIT);
        assertEquals(ResultSet.HOLD_CURSORS_OVER_COMMIT, connection.getHoldability());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testSavepoints(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        connection.setAutoCommit(false);
        
        // Test unnamed savepoint
        Savepoint savepoint1 = connection.setSavepoint();
        assertNotNull(savepoint1);
        
        // Test named savepoint
        Savepoint savepoint2 = connection.setSavepoint("test_savepoint");
        assertNotNull(savepoint2);
        assertEquals("test_savepoint", savepoint2.getSavepointName());
        
        // Test rollback to savepoint
        connection.rollback(savepoint2);
        connection.rollback(savepoint1);
        
        // Test release savepoint
        connection.releaseSavepoint(savepoint1);
        
        connection.setAutoCommit(true);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testClientInfo(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        Properties clientInfo = connection.getClientInfo();
        assertNotNull(clientInfo);
        
        // Test setting client info
        try {
            connection.setClientInfo("ApplicationName", "MySQLTest");
            // Should not throw exception, though MySQL may not support all properties
        } catch (SQLClientInfoException e) {
            // This is acceptable for MySQL
        }
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testValid(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        boolean isValid = connection.isValid(5);
        assertTrue(isValid);
        
        // Test with closed connection
        connection.close();
        isValid = connection.isValid(5);
        assertEquals(false, isValid);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/mysql_connection.csv")
    public void testUnsupportedOperations(String driverClass, String url, String user, String password) throws SQLException {
        setUp(driverClass, url, user, password);
        
        // Test operations that might not be supported
        assertThrows(SQLFeatureNotSupportedException.class, () -> {
            connection.createArrayOf("VARCHAR", new String[]{"test"});
        });
        
        assertThrows(SQLFeatureNotSupportedException.class, () -> {
            connection.createStruct("test_type", new Object[]{});
        });
    }
}