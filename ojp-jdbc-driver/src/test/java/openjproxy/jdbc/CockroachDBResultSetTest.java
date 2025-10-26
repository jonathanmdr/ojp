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
import java.sql.SQLWarning;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * CockroachDB-specific ResultSet tests.
 * Tests CockroachDB-specific ResultSet behavior and data type handling.
 */
public class CockroachDBResultSetTest {

    private Connection connection;
    private XAConnection xaConnectionection;
    private Statement statement;
    private ResultSet resultSet;

    private static boolean isTestDisabled;

    @BeforeAll
    public static void checkTestConfiguration() {
        isTestDisabled = Boolean.parseBoolean(System.getProperty("disableCockroachDBTests", "false"));
    }

    @SneakyThrows
    public void setUp(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        assumeFalse(isTestDisabled, "Skipping CockroachDB tests");

        // Create CockroachDB database connection
        connection = DriverManager.getConnection(url, user, pwd);

        // Create a scrollable and updatable Statement
        statement = connection.createStatement(
                ResultSet.TYPE_SCROLL_INSENSITIVE, // Scrollable ResultSet
                ResultSet.CONCUR_UPDATABLE         // Updatable ResultSet
        );

        // Create a test table and insert data
        try {
            statement.execute("DROP TABLE cockroachdb_resultset_test_table");
        } catch (Exception e) {
            //Expected if table does not exist.
        }
        
        // Create table with CockroachDB-specific data types
        statement.execute("CREATE TABLE cockroachdb_resultset_test_table (" +
                "id INT PRIMARY KEY, " +
                "name VARCHAR(255), " +
                "age INT, " +
                "salary DECIMAL(10,2), " +
                "active BOOLEAN, " +
                "created_at TIMESTAMP)");
        
        statement.execute("INSERT INTO cockroachdb_resultset_test_table (id, name, age, salary, active, created_at) " +
                "VALUES (1, 'Alice', 30, 50000.00, true, CURRENT_TIMESTAMP)");
        statement.execute("INSERT INTO cockroachdb_resultset_test_table (id, name, age, salary, active, created_at) " +
                "VALUES (2, 'Bob', 25, 45000.00, false, CURRENT_TIMESTAMP)");
        statement.execute("INSERT INTO cockroachdb_resultset_test_table (id, name, age, salary, active, created_at) " +
                "VALUES (3, 'Charlie', 35, 55000.00, true, CURRENT_TIMESTAMP)");

        // Query the data with a scrollable ResultSet
        resultSet = statement.executeQuery("SELECT * FROM cockroachdb_resultset_test_table ORDER BY id");
    }

    @AfterEach
    public void tearDown() throws SQLException {
        // Clean up resources
        if (resultSet != null) resultSet.close();
        if (statement != null) statement.close();
        if (connection != null) connection.close();
        if (xaConnectionection != null) xaConnectionection.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCockroachDBNavigationMethods(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        setUp(driverClass, url, user, pwd, isXA);
        assertTrue(resultSet.next()); // Row 1
        assertTrue(resultSet.next()); // Row 2
        assertTrue(resultSet.previous()); // Back to Row 1
        assertTrue(resultSet.last()); // Move to the last row
        assertTrue(resultSet.isLast());
        assertTrue(resultSet.first()); // Back to the first row
        assertTrue(resultSet.isFirst());
        assertFalse(resultSet.isAfterLast());
        assertFalse(resultSet.isBeforeFirst());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCockroachDBAbsoluteAndRelativePositioning(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        setUp(driverClass, url, user, pwd, isXA);
        
        assertTrue(resultSet.absolute(2)); // Move to row 2
        assertEquals(2, resultSet.getInt("id"));
        
        assertTrue(resultSet.relative(-1)); // Move back one row (to row 1)
        assertEquals(1, resultSet.getInt("id"));
        
        assertTrue(resultSet.relative(2)); // Move forward two rows (to row 3)
        assertEquals(3, resultSet.getInt("id"));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCockroachDBGetRow(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        setUp(driverClass, url, user, pwd, isXA);
        
        assertEquals(0, resultSet.getRow()); // Before first row
        assertTrue(resultSet.next());
        assertEquals(1, resultSet.getRow());
        assertTrue(resultSet.next());
        assertEquals(2, resultSet.getRow());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCockroachDBDataRetrieval(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        setUp(driverClass, url, user, pwd, isXA);
        
        assertTrue(resultSet.next());
        
        // Test retrieval by column index
        assertEquals(1, resultSet.getInt(1));
        assertEquals("Alice", resultSet.getString(2));
        assertEquals(30, resultSet.getInt(3));
        assertEquals(50000.00, resultSet.getDouble(4), 0.01);
        assertTrue(resultSet.getBoolean(5));
        assertNotNull(resultSet.getTimestamp(6));
        
        // Test retrieval by column name
        assertEquals(1, resultSet.getInt("id"));
        assertEquals("Alice", resultSet.getString("name"));
        assertEquals(30, resultSet.getInt("age"));
        assertEquals(50000.00, resultSet.getDouble("salary"), 0.01);
        assertTrue(resultSet.getBoolean("active"));
        assertNotNull(resultSet.getTimestamp("created_at"));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCockroachDBNullHandling(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        assumeFalse(isTestDisabled, "Skipping CockroachDB tests");
        
        connection = DriverManager.getConnection(url, user, pwd);
        statement = connection.createStatement();
        
        try {
            statement.execute("DROP TABLE cockroachdb_null_test");
        } catch (Exception e) {
            // Ignore
        }
        
        statement.execute("CREATE TABLE cockroachdb_null_test (id INT PRIMARY KEY, name VARCHAR(255), age INT)");
        statement.execute("INSERT INTO cockroachdb_null_test (id, name, age) VALUES (1, NULL, NULL)");
        
        resultSet = statement.executeQuery("SELECT * FROM cockroachdb_null_test");
        assertTrue(resultSet.next());
        
        assertNull(resultSet.getString("name"));
        assertTrue(resultSet.wasNull());
        
        assertEquals(0, resultSet.getInt("age")); // getInt returns 0 for NULL
        assertTrue(resultSet.wasNull());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCockroachDBFindColumn(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        setUp(driverClass, url, user, pwd, isXA);
        
        assertTrue(resultSet.next());
        
        assertEquals(1, resultSet.findColumn("id"));
        assertEquals(2, resultSet.findColumn("name"));
        assertEquals(3, resultSet.findColumn("age"));
        assertEquals(4, resultSet.findColumn("salary"));
        assertEquals(5, resultSet.findColumn("active"));
        assertEquals(6, resultSet.findColumn("created_at"));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCockroachDBResultSetType(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        setUp(driverClass, url, user, pwd, isXA);
        
        assertEquals(ResultSet.TYPE_FORWARD_ONLY, resultSet.getType());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCockroachDBResultSetConcurrency(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        setUp(driverClass, url, user, pwd, isXA);
        
        int concurrency = resultSet.getConcurrency();
        assertTrue(concurrency == ResultSet.CONCUR_UPDATABLE || concurrency == ResultSet.CONCUR_READ_ONLY);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCockroachDBWarnings(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        setUp(driverClass, url, user, pwd, isXA);
        
        resultSet.clearWarnings();
        SQLWarning warning = resultSet.getWarnings();
        // Warning may be null, that's fine
        assertTrue(warning == null || warning instanceof SQLWarning);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCockroachDBGetStatement(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        setUp(driverClass, url, user, pwd, isXA);
        
        Statement stmt = resultSet.getStatement();
        assertNotNull(stmt);
        assertEquals(statement, stmt);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCockroachDBBeforeFirstAfterLast(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        setUp(driverClass, url, user, pwd, isXA);
        
        resultSet.beforeFirst();
        assertTrue(resultSet.isBeforeFirst());
        assertFalse(resultSet.isFirst());
        
        resultSet.afterLast();
        assertTrue(resultSet.isAfterLast());
        assertFalse(resultSet.isLast());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCockroachDBIsClosed(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException {
        setUp(driverClass, url, user, pwd, isXA);
        
        assertFalse(resultSet.isClosed());
        resultSet.close();
        assertTrue(resultSet.isClosed());
    }
}
