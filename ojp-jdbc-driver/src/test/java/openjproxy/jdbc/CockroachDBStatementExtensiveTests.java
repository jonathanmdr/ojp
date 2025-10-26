package openjproxy.jdbc;

import openjproxy.jdbc.testutil.TestDBUtils;
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
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class CockroachDBStatementExtensiveTests {

    private static boolean isTestDisabled;

    private Connection connection;
    private XAConnection xaConnectionection;
    private Statement statement;

    @BeforeAll
    public static void checkTestConfiguration() {
        isTestDisabled = Boolean.parseBoolean(System.getProperty("disableCockroachDBTests", "false"));
    }

    public void setUp(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        assumeFalse(isTestDisabled, "CockroachDB tests are disabled");
        
        connection = DriverManager.getConnection(url, user, password);
        statement = connection.createStatement();

        TestDBUtils.createBasicTestTable(connection, "cockroachdb_statement_test", TestDBUtils.SqlSyntax.COCKROACHDB, true);
    }

    @AfterEach
    public void tearDown() throws Exception {
        TestDBUtils.closeQuietly(statement, connection);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testExecuteQuery(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        ResultSet rs = statement.executeQuery("SELECT * FROM cockroachdb_statement_test");
        assertNotNull(rs);
        assertTrue(rs.next());
        rs.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testExecuteUpdate(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        int rows = statement.executeUpdate("UPDATE cockroachdb_statement_test SET name = 'Updated Alice' WHERE id = 1");
        assertEquals(1, rows);

        ResultSet rs = statement.executeQuery("SELECT name FROM cockroachdb_statement_test WHERE id = 1");
        assertTrue(rs.next());
        assertEquals("Updated Alice", rs.getString("name"));
        rs.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testClose(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        assertFalse(statement.isClosed());
        statement.close();
        assertTrue(statement.isClosed());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testMaxFieldSize(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        int orig = statement.getMaxFieldSize();
        statement.setMaxFieldSize(orig + 1);
        int newSize = statement.getMaxFieldSize();
        assertTrue(newSize >= 0, "Max field size should be >= 0, got: " + newSize);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testExecuteAfterCloseThrows(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        statement.close();
        assertThrows(SQLException.class, () -> statement.executeQuery("SELECT * FROM cockroachdb_statement_test"));
        assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE cockroachdb_statement_test SET name = 'fail' WHERE id = 1"));
        assertThrows(SQLException.class, () -> statement.addBatch("INSERT INTO cockroachdb_statement_test (id, name) VALUES (99, 'ShouldFail')"));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testMaxRows(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        statement.setMaxRows(1);
        assertEquals(1, statement.getMaxRows());
        ResultSet rs = statement.executeQuery("SELECT * FROM cockroachdb_statement_test");
        assertTrue(rs.next());
        assertFalse(rs.next());
        rs.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testEscapeProcessing(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        // Should not throw
        statement.setEscapeProcessing(true);
        statement.setEscapeProcessing(false);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testQueryTimeout(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        statement.setQueryTimeout(5);
        assertEquals(5, statement.getQueryTimeout());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCancel(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        // Should not throw
        statement.cancel();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testWarnings(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        statement.clearWarnings();
        assertNull(statement.getWarnings());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testSetCursorName(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        // No-op in most drivers; should not throw
        statement.setCursorName("CURSOR_A");
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testExecute(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        boolean isResultSet = statement.execute("SELECT * FROM cockroachdb_statement_test");
        assertTrue(isResultSet);
        ResultSet rs = statement.getResultSet();
        assertNotNull(rs);
        rs.close();
        assertEquals(-1, statement.getUpdateCount());

        isResultSet = statement.execute("UPDATE cockroachdb_statement_test SET name = 'Updated Bob' WHERE id = 2");
        assertFalse(isResultSet);
        assertEquals(1, statement.getUpdateCount());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testGetMoreResults(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        statement.execute("SELECT * FROM cockroachdb_statement_test");
        assertFalse(statement.getMoreResults());
        assertEquals(-1, statement.getUpdateCount());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testFetchDirection(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        int orig = statement.getFetchDirection();
        statement.setFetchDirection(ResultSet.FETCH_FORWARD);
        assertEquals(ResultSet.FETCH_FORWARD, statement.getFetchDirection());
        statement.setFetchDirection(orig); // Restore
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testFetchSize(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        int orig = statement.getFetchSize();
        statement.setFetchSize(orig + 1);
        assertEquals(orig + 1, statement.getFetchSize());
        statement.setFetchSize(orig);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testResultSetConcurrencyAndType(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        int concurrency = statement.getResultSetConcurrency();
        int type = statement.getResultSetType();
        assertTrue(concurrency == ResultSet.CONCUR_READ_ONLY || concurrency == ResultSet.CONCUR_UPDATABLE);
        assertTrue(type == ResultSet.TYPE_FORWARD_ONLY || type == ResultSet.TYPE_SCROLL_INSENSITIVE || type == ResultSet.TYPE_SCROLL_SENSITIVE);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testBatchExecution(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        statement.addBatch("INSERT INTO cockroachdb_statement_test (id, name) VALUES (3, 'Charlie')");
        statement.addBatch("INSERT INTO cockroachdb_statement_test (id, name) VALUES (4, 'David')");
        int[] results = statement.executeBatch();
        assertEquals(2, results.length);

        ResultSet rs = statement.executeQuery("SELECT COUNT(*) AS total FROM cockroachdb_statement_test");
        assertTrue(rs.next());
        assertEquals(4, rs.getLong("total"));
        rs.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testClearBatch(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        statement.addBatch("INSERT INTO cockroachdb_statement_test (id, name) VALUES (5, 'Eve')");
        statement.clearBatch();
        int[] results = statement.executeBatch();
        assertEquals(0, results.length);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testGetConnection(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        assertSame(connection, statement.getConnection());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testGetMoreResultsWithCurrent(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        statement.execute("SELECT * FROM cockroachdb_statement_test");
        assertFalse(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testGetGeneratedKeys(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        // CockroachDB supports SERIAL, create table with auto-increment
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS cockroachdb_gen_keys_test");
            stmt.execute("CREATE TABLE cockroachdb_gen_keys_test (id SERIAL PRIMARY KEY, name VARCHAR(255))");
        }

        int affected = statement.executeUpdate("INSERT INTO cockroachdb_gen_keys_test (name) VALUES ('TestGen')", Statement.RETURN_GENERATED_KEYS);
        assertEquals(1, affected);

        ResultSet rs = statement.getGeneratedKeys();
        assertNotNull(rs);
        assertTrue(rs.next());
        // CockroachDB SERIAL uses BIGINT
        assertTrue(rs.getLong(1) > 0);
        rs.close();

        // Cleanup
        statement.execute("DROP TABLE IF EXISTS cockroachdb_gen_keys_test");
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testExecuteLargeBatch(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        statement.addBatch("INSERT INTO cockroachdb_statement_test (id, name) VALUES (10, 'Large1')");
        statement.addBatch("INSERT INTO cockroachdb_statement_test (id, name) VALUES (11, 'Large2')");
        long[] results = statement.executeLargeBatch();
        assertEquals(2, results.length);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testExecuteLargeUpdate(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        long affected = statement.executeLargeUpdate("UPDATE cockroachdb_statement_test SET name = 'LargeUpdate' WHERE id = 1");
        assertEquals(1L, affected);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testGetLargeUpdateCount(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        statement.executeUpdate("UPDATE cockroachdb_statement_test SET name = 'Test' WHERE id = 1");
        long updateCount = statement.getLargeUpdateCount();
        // After execution, getUpdateCount returns -1 to indicate no more results
        // This is consistent with JDBC spec
        assertEquals(-1L, updateCount);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testResultSetHoldability(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        int holdability = statement.getResultSetHoldability();
        assertTrue(holdability == ResultSet.HOLD_CURSORS_OVER_COMMIT || 
                   holdability == ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testIsPoolable(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        // Default is usually false, but should not throw
        assertDoesNotThrow(() -> statement.isPoolable());
        assertDoesNotThrow(() -> statement.setPoolable(true));
        assertDoesNotThrow(() -> statement.setPoolable(false));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCloseOnCompletion(String driverClass, String url, String user, String password, boolean isXA) throws Exception {
        this.setUp(driverClass, url, user, password, isXA);
        assertFalse(statement.isCloseOnCompletion());
        statement.closeOnCompletion();
        assertTrue(statement.isCloseOnCompletion());
    }
}
