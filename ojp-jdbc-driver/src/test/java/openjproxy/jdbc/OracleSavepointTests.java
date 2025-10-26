package openjproxy.jdbc;

import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.openjproxy.jdbc.xa.OjpXADataSource;
import javax.sql.XAConnection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class OracleSavepointTests {

    private static boolean isTestDisabled;
    private Connection connection;
    private XAConnection xaConnection;

    @BeforeAll
    public static void checkTestConfiguration() {
        isTestDisabled = !Boolean.parseBoolean(System.getProperty("enableOracleTests", "false"));
    }

    @SneakyThrows
    public void setUp(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        assumeFalse(isTestDisabled, "Oracle tests are disabled");
        
        connection = DriverManager.getConnection(url, user, pwd);
        connection.setAutoCommit(false);
        
        Statement stmt = connection.createStatement();
        try {
            stmt.execute("DROP TABLE savepoint_test_table");
        } catch (SQLException e) {
            // Table might not exist
        }
        
        // Oracle-specific CREATE TABLE syntax
        stmt.execute(
            "CREATE TABLE savepoint_test_table (id NUMBER(10) PRIMARY KEY, name VARCHAR2(255))"
        );
        stmt.close();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (connection != null) connection.close();
        if (xaConnection != null) xaConnection.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/oracle_connections.csv")
    public void testUnnamedSavepoint(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        setUp(driverClass, url, user, pwd, isXA);
        
        connection.createStatement().execute("INSERT INTO savepoint_test_table (id, name) VALUES (1, 'Alice')");
        Savepoint savepoint = connection.setSavepoint();

        connection.createStatement().execute("INSERT INTO savepoint_test_table (id, name) VALUES (2, 'Bob')");
        connection.rollback(savepoint);

        ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM savepoint_test_table ORDER BY id DESC");
        assertTrue(resultSet.next());
        assertEquals(1, resultSet.getInt("id"));
        assertEquals("Alice", resultSet.getString("name"));
        assertFalse(resultSet.next()); // Should only have one row
        resultSet.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/oracle_connections.csv")
    public void testNamedSavepoint(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        setUp(driverClass, url, user, pwd, isXA);
        
        connection.createStatement().execute("INSERT INTO savepoint_test_table (id, name) VALUES (1, 'Alice')");
        Savepoint savepoint = connection.setSavepoint("sp1");
        
        assertEquals("sp1", savepoint.getSavepointName());

        connection.createStatement().execute("INSERT INTO savepoint_test_table (id, name) VALUES (2, 'Bob')");
        connection.createStatement().execute("INSERT INTO savepoint_test_table (id, name) VALUES (3, 'Charlie')");
        
        connection.rollback(savepoint);

        ResultSet resultSet = connection.createStatement().executeQuery("SELECT COUNT(*) AS cnt FROM savepoint_test_table");
        assertTrue(resultSet.next());
        assertEquals(1, resultSet.getInt("cnt")); // Should only have Alice
        resultSet.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/oracle_connections.csv")
    public void testMultipleSavepoints(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        setUp(driverClass, url, user, pwd, isXA);
        
        connection.createStatement().execute("INSERT INTO savepoint_test_table (id, name) VALUES (1, 'Alice')");
        Savepoint sp1 = connection.setSavepoint("sp1");

        connection.createStatement().execute("INSERT INTO savepoint_test_table (id, name) VALUES (2, 'Bob')");
        Savepoint sp2 = connection.setSavepoint("sp2");

        connection.createStatement().execute("INSERT INTO savepoint_test_table (id, name) VALUES (3, 'Charlie')");
        
        // Rollback to sp2 - should remove Charlie but keep Bob
        connection.rollback(sp2);
        
        ResultSet resultSet = connection.createStatement().executeQuery("SELECT COUNT(*) AS cnt FROM savepoint_test_table");
        assertTrue(resultSet.next());
        assertEquals(2, resultSet.getInt("cnt")); // Should have Alice and Bob
        resultSet.close();
        
        // Now rollback to sp1 - should only keep Alice
        connection.rollback(sp1);
        
        resultSet = connection.createStatement().executeQuery("SELECT COUNT(*) AS cnt FROM savepoint_test_table");
        assertTrue(resultSet.next());
        assertEquals(1, resultSet.getInt("cnt")); // Should only have Alice
        resultSet.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/oracle_connections.csv")
    public void testReleaseSavepoint(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        setUp(driverClass, url, user, pwd, isXA);
        
        connection.createStatement().execute("INSERT INTO savepoint_test_table (id, name) VALUES (1, 'Alice')");
        Savepoint savepoint = connection.setSavepoint("sp1");

        connection.createStatement().execute("INSERT INTO savepoint_test_table (id, name) VALUES (2, 'Bob')");
        
        // Release the savepoint
        connection.releaseSavepoint(savepoint);
        
        // After releasing, we can't rollback to it, but data should remain
        ResultSet resultSet = connection.createStatement().executeQuery("SELECT COUNT(*) AS cnt FROM savepoint_test_table");
        assertTrue(resultSet.next());
        assertEquals(2, resultSet.getInt("cnt")); // Should have both Alice and Bob
        resultSet.close();
        
        // Trying to rollback to released savepoint should throw exception
        connection.rollback(savepoint);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/oracle_connections.csv")
    public void testSavepointAfterCommit(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        setUp(driverClass, url, user, pwd, isXA);
        
        connection.createStatement().execute("INSERT INTO savepoint_test_table (id, name) VALUES (1, 'Alice')");
        Savepoint savepoint = connection.setSavepoint("sp1");

        connection.createStatement().execute("INSERT INTO savepoint_test_table (id, name) VALUES (2, 'Bob')");
        
        // Commit the transaction
        connection.commit();
        
        // After commit, savepoint should be invalid
        connection.rollback(savepoint);
        
        // Data should still be there after commit
        ResultSet resultSet = connection.createStatement().executeQuery("SELECT COUNT(*) AS cnt FROM savepoint_test_table");
        assertTrue(resultSet.next());
        assertEquals(2, resultSet.getInt("cnt")); // Should have both Alice and Bob
        resultSet.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/oracle_connections.csv")
    public void testSavepointMetadata(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        setUp(driverClass, url, user, pwd, isXA);
        
        // Test that Oracle supports savepoints
        assertTrue(connection.getMetaData().supportsSavepoints());
        
        // Create savepoints and verify their properties
        Savepoint unnamedSp = connection.setSavepoint();
        assertNotNull(unnamedSp);
        assertTrue(unnamedSp.getSavepointId() > 0);
        assertThrows(SQLException.class, () -> unnamedSp.getSavepointName());
        
        Savepoint namedSp = connection.setSavepoint("test_sp");
        assertNotNull(namedSp);
        assertEquals("test_sp", namedSp.getSavepointName());
        assertThrows(SQLException.class, () -> namedSp.getSavepointId());
        
        // Clean up
        connection.releaseSavepoint(unnamedSp);
        connection.releaseSavepoint(namedSp);
    }
}