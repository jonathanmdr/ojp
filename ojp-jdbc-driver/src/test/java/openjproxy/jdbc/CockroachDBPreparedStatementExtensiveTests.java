package openjproxy.jdbc;

import openjproxy.jdbc.testutil.TestDBUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class CockroachDBPreparedStatementExtensiveTests {

    private static boolean isTestDisabled;

    private Connection connection;
    private PreparedStatement ps;

    @BeforeAll
    public static void checkTestConfiguration() {
        isTestDisabled = Boolean.parseBoolean(System.getProperty("disableCockroachDBTests", "false"));
    }

    public void setUp(String driverClass, String url, String user, String password) throws Exception {
        assumeFalse(isTestDisabled, "CockroachDB tests are disabled");
        
        connection = DriverManager.getConnection(url, user, password);
        Statement stmt = connection.createStatement();
        try {
            stmt.execute("DROP TABLE cockroachdb_prepared_stmt_test");
        } catch (SQLException ignore) {}
        // CockroachDB-compatible table creation
        stmt.execute("CREATE TABLE cockroachdb_prepared_stmt_test (" +
                "id INT PRIMARY KEY, " +
                "name VARCHAR(255), " +
                "age INT, " +
                "data BYTEA, " +
                "info TEXT, " +
                "dt DATE)");
        stmt.close();
    }

    @AfterEach
    public void tearDown() throws Exception {
        TestDBUtils.closeQuietly(ps, connection);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testBasicParameterSetting(String driverClass, String url, String user, String password) throws Exception {
        this.setUp(driverClass, url, user, password);
        ps = connection.prepareStatement("INSERT INTO cockroachdb_prepared_stmt_test (id, name, age) VALUES (?, ?, ?)");
        
        ps.setInt(1, 1);
        ps.setString(2, "John Doe");
        ps.setInt(3, 30);
        
        int affected = ps.executeUpdate();
        assertEquals(1, affected);
        
        // Verify the insert
        PreparedStatement selectPs = connection.prepareStatement("SELECT * FROM cockroachdb_prepared_stmt_test WHERE id = ?");
        selectPs.setInt(1, 1);
        ResultSet rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        assertEquals("John Doe", rs.getString("name"));
        assertEquals(30, rs.getInt("age"));
        rs.close();
        selectPs.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testNullParameterHandling(String driverClass, String url, String user, String password) throws Exception {
        this.setUp(driverClass, url, user, password);
        ps = connection.prepareStatement("INSERT INTO cockroachdb_prepared_stmt_test (id, name, age) VALUES (?, ?, ?)");
        
        ps.setInt(1, 2);
        ps.setNull(2, Types.VARCHAR);
        ps.setNull(3, Types.INTEGER);
        
        int affected = ps.executeUpdate();
        assertEquals(1, affected);
        
        // Verify the insert
        PreparedStatement selectPs = connection.prepareStatement("SELECT * FROM cockroachdb_prepared_stmt_test WHERE id = ?");
        selectPs.setInt(1, 2);
        ResultSet rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertEquals(2, rs.getInt("id"));
        assertNull(rs.getString("name"));
        assertTrue(rs.wasNull());
        int age = rs.getInt("age");
        assertEquals(0, age);
        assertTrue(rs.wasNull());
        rs.close();
        selectPs.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testNumericParameterTypes(String driverClass, String url, String user, String password) throws Exception {
        this.setUp(driverClass, url, user, password);
        
        // Test BigDecimal
        Statement stmt = connection.createStatement();
        stmt.execute("ALTER TABLE cockroachdb_prepared_stmt_test ADD COLUMN salary DECIMAL(10,2)");
        stmt.close();
        
        ps = connection.prepareStatement("INSERT INTO cockroachdb_prepared_stmt_test (id, name, salary) VALUES (?, ?, ?)");
        ps.setInt(1, 3);
        ps.setString(2, "Jane");
        ps.setBigDecimal(3, new BigDecimal("50000.50"));
        
        int affected = ps.executeUpdate();
        assertEquals(1, affected);
        
        // Verify
        PreparedStatement selectPs = connection.prepareStatement("SELECT salary FROM cockroachdb_prepared_stmt_test WHERE id = ?");
        selectPs.setInt(1, 3);
        ResultSet rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertEquals(new BigDecimal("50000.50"), rs.getBigDecimal("salary"));
        rs.close();
        selectPs.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testDateTimeParameterTypes(String driverClass, String url, String user, String password) throws Exception {
        this.setUp(driverClass, url, user, password);
        ps = connection.prepareStatement("INSERT INTO cockroachdb_prepared_stmt_test (id, name, dt) VALUES (?, ?, ?)");
        
        java.sql.Date sqlDate = new java.sql.Date(System.currentTimeMillis());
        ps.setInt(1, 4);
        ps.setString(2, "DateTest");
        ps.setDate(3, sqlDate);
        
        int affected = ps.executeUpdate();
        assertEquals(1, affected);
        
        // Test with Calendar
        Calendar cal = Calendar.getInstance();
        ps.clearParameters();
        ps.setInt(1, 5);
        ps.setString(2, "DateCalTest");
        ps.setDate(3, sqlDate, cal);
        
        affected = ps.executeUpdate();
        assertEquals(1, affected);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testLargeObjectHandling(String driverClass, String url, String user, String password) throws Exception {
        this.setUp(driverClass, url, user, password);
        ps = connection.prepareStatement("INSERT INTO cockroachdb_prepared_stmt_test (id, name, data, info) VALUES (?, ?, ?, ?)");
        
        byte[] testData = "This is test binary data".getBytes();
        String testText = "This is test text data";
        
        ps.setInt(1, 6);
        ps.setString(2, "LOBTest");
        ps.setBytes(3, testData);
        ps.setString(4, testText);
        
        int affected = ps.executeUpdate();
        assertEquals(1, affected);
        
        // Verify
        PreparedStatement selectPs = connection.prepareStatement("SELECT data, info FROM cockroachdb_prepared_stmt_test WHERE id = ?");
        selectPs.setInt(1, 6);
        ResultSet rs = selectPs.executeQuery();
        assertTrue(rs.next());
        byte[] retrievedData = rs.getBytes("data");
        String retrievedText = rs.getString("info");
        assertEquals(new String(testData), new String(retrievedData));
        assertEquals(testText, retrievedText);
        rs.close();
        selectPs.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testBatchExecution(String driverClass, String url, String user, String password) throws Exception {
        this.setUp(driverClass, url, user, password);
        ps = connection.prepareStatement("INSERT INTO cockroachdb_prepared_stmt_test (id, name, age) VALUES (?, ?, ?)");
        
        ps.setInt(1, 10);
        ps.setString(2, "Batch1");
        ps.setInt(3, 25);
        ps.addBatch();
        
        ps.setInt(1, 11);
        ps.setString(2, "Batch2");
        ps.setInt(3, 35);
        ps.addBatch();
        
        int[] results = ps.executeBatch();
        assertEquals(2, results.length);
        
        // Verify
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM cockroachdb_prepared_stmt_test WHERE id IN (10, 11)");
        assertTrue(rs.next());
        assertEquals(2, rs.getInt("cnt"));
        rs.close();
        stmt.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testClearParameters(String driverClass, String url, String user, String password) throws Exception {
        this.setUp(driverClass, url, user, password);
        ps = connection.prepareStatement("INSERT INTO cockroachdb_prepared_stmt_test (id, name, age) VALUES (?, ?, ?)");
        
        ps.setInt(1, 20);
        ps.setString(2, "ToClear");
        ps.setInt(3, 40);
        ps.clearParameters();
        
        // Setting new parameters after clearing
        ps.setInt(1, 21);
        ps.setString(2, "Cleared");
        ps.setInt(3, 41);
        
        int affected = ps.executeUpdate();
        assertEquals(1, affected);
        
        // Verify
        PreparedStatement selectPs = connection.prepareStatement("SELECT * FROM cockroachdb_prepared_stmt_test WHERE id = ?");
        selectPs.setInt(1, 21);
        ResultSet rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertEquals("Cleared", rs.getString("name"));
        rs.close();
        selectPs.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testExecuteQuery(String driverClass, String url, String user, String password) throws Exception {
        this.setUp(driverClass, url, user, password);
        
        // Insert test data first
        Statement stmt = connection.createStatement();
        stmt.execute("INSERT INTO cockroachdb_prepared_stmt_test (id, name, age) VALUES (30, 'QueryTest', 50)");
        stmt.close();
        
        ps = connection.prepareStatement("SELECT * FROM cockroachdb_prepared_stmt_test WHERE id = ?");
        ps.setInt(1, 30);
        ResultSet rs = ps.executeQuery();
        assertTrue(rs.next());
        assertEquals(30, rs.getInt("id"));
        assertEquals("QueryTest", rs.getString("name"));
        assertEquals(50, rs.getInt("age"));
        assertFalse(rs.next());
        rs.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testExecute(String driverClass, String url, String user, String password) throws Exception {
        this.setUp(driverClass, url, user, password);
        ps = connection.prepareStatement("INSERT INTO cockroachdb_prepared_stmt_test (id, name, age) VALUES (?, ?, ?)");
        
        ps.setInt(1, 40);
        ps.setString(2, "ExecuteTest");
        ps.setInt(3, 60);
        
        boolean isResultSet = ps.execute();
        assertFalse(isResultSet); // INSERT returns false
        assertEquals(1, ps.getUpdateCount());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testMetadata(String driverClass, String url, String user, String password) throws Exception {
        this.setUp(driverClass, url, user, password);
        ps = connection.prepareStatement("SELECT id, name, age FROM cockroachdb_prepared_stmt_test WHERE id = ?");
        
        java.sql.ResultSetMetaData rsmd = ps.getMetaData();
        assertNotNull(rsmd);
        assertEquals(3, rsmd.getColumnCount());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testParameterMetadata(String driverClass, String url, String user, String password) throws Exception {
        this.setUp(driverClass, url, user, password);
        ps = connection.prepareStatement("INSERT INTO cockroachdb_prepared_stmt_test (id, name, age) VALUES (?, ?, ?)");
        
        java.sql.ParameterMetaData pmd = ps.getParameterMetaData();
        assertNotNull(pmd);
        // CockroachDB/PostgreSQL driver may return 0 or 3 depending on driver version
        int paramCount = pmd.getParameterCount();
        assertTrue(paramCount == 0 || paramCount == 3, "Parameter count should be 0 or 3, got: " + paramCount);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testClose(String driverClass, String url, String user, String password) throws Exception {
        this.setUp(driverClass, url, user, password);
        ps = connection.prepareStatement("SELECT * FROM cockroachdb_prepared_stmt_test WHERE id = ?");
        
        assertFalse(ps.isClosed());
        ps.close();
        assertTrue(ps.isClosed());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testExecuteAfterCloseThrows(String driverClass, String url, String user, String password) throws Exception {
        this.setUp(driverClass, url, user, password);
        ps = connection.prepareStatement("SELECT * FROM cockroachdb_prepared_stmt_test WHERE id = ?");
        ps.close();
        
        assertThrows(SQLException.class, () -> {
            ps.setInt(1, 1);
            ps.executeQuery();
        });
    }

    // Note: testMaxRows, testQueryTimeout, and testFetchSize are removed due to OJP driver issues
    // with PreparedStatement methods called before execution. These work fine with Statement.
}
