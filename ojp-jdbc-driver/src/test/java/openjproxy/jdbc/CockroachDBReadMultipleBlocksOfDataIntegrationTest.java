package openjproxy.jdbc;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.openjproxy.jdbc.xa.OjpXADataSource;
import javax.sql.XAConnection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import static openjproxy.helpers.SqlHelper.executeUpdate;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * CockroachDB-specific multiple blocks of data integration tests.
 * Tests CockroachDB pagination and large result set handling.
 */
public class CockroachDBReadMultipleBlocksOfDataIntegrationTest {

    private static boolean isTestDisabled;

    @BeforeAll
    public static void checkTestConfiguration() {
        isTestDisabled = Boolean.parseBoolean(System.getProperty("disableCockroachDBTests", "false"));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCockroachDBMultiplePagesOfRows(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException, ClassNotFoundException {
        assumeFalse(isTestDisabled, "Skipping CockroachDB tests");
        
        Connection conn;
        XAConnection xaConn = null;
        if (isXA) {
            OjpXADataSource xaDataSource = new OjpXADataSource();
            xaDataSource.setUrl(url);
            xaDataSource.setUser(user);
            xaDataSource.setPassword(pwd);
            xaConn = xaDataSource.getXAConnection(user, pwd);
            conn = xaConn.getConnection();
        } else {
            conn = DriverManager.getConnection(url, user, pwd);
        }

        int totalRecords = 1000;
        System.out.println("Testing CockroachDB retrieving " + totalRecords + " records from url -> " + url);

        try {
            executeUpdate(conn, "DROP TABLE cockroachdb_read_blocks_test_multi");
        } catch (Exception e) {
            //Does not matter
        }
        
        // Create table with CockroachDB-specific syntax
        executeUpdate(conn, "CREATE TABLE cockroachdb_read_blocks_test_multi(" +
                "id INT NOT NULL PRIMARY KEY, " +
                "title VARCHAR(50) NOT NULL)");

        for (int i = 0; i < totalRecords; i++) {
            executeUpdate(conn,
                    "INSERT INTO cockroachdb_read_blocks_test_multi (id, title) VALUES (" + i + ", 'COCKROACHDB_TITLE_" + i + "')"
            );
        }

        java.sql.PreparedStatement psSelect = conn.prepareStatement("SELECT * FROM cockroachdb_read_blocks_test_multi ORDER BY id");
        ResultSet resultSet = psSelect.executeQuery();

        for (int i = 0; i < totalRecords; i++) {
            resultSet.next();
            int id = resultSet.getInt(1);
            String title = resultSet.getString(2);
            Assert.assertEquals(i, id);
            Assert.assertEquals("COCKROACHDB_TITLE_" + i, title);
        }

        executeUpdate(conn, "DELETE FROM cockroachdb_read_blocks_test_multi");

        ResultSet resultSetAfterDeletion = psSelect.executeQuery();
        Assert.assertFalse(resultSetAfterDeletion.next());

        resultSet.close();
        psSelect.close();
        if (xaConn != null) xaConn.close();
        conn.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCockroachDBLargeDataSetPagination(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException, ClassNotFoundException {
        assumeFalse(isTestDisabled, "Skipping CockroachDB tests");
        
        Connection conn;
        XAConnection xaConn = null;
        if (isXA) {
            OjpXADataSource xaDataSource = new OjpXADataSource();
            xaDataSource.setUrl(url);
            xaDataSource.setUser(user);
            xaDataSource.setPassword(pwd);
            xaConn = xaDataSource.getXAConnection(user, pwd);
            conn = xaConn.getConnection();
        } else {
            conn = DriverManager.getConnection(url, user, pwd);
        }

        System.out.println("Testing CockroachDB large dataset pagination for url -> " + url);

        try {
            executeUpdate(conn, "DROP TABLE cockroachdb_pagination_test");
        } catch (Exception e) {
            //Does not matter
        }
        
        // Create table with CockroachDB-specific data types
        executeUpdate(conn, "CREATE TABLE cockroachdb_pagination_test(" +
                "id INT PRIMARY KEY, " +
                "name VARCHAR(100) NOT NULL, " +
                "value DECIMAL(19,2), " +
                "description TEXT)");

        // Insert 5000 records for pagination testing
        int totalRecords = 5000;
        for (int i = 1; i <= totalRecords; i++) {
            executeUpdate(conn,
                    "INSERT INTO cockroachdb_pagination_test (id, name, value, description) " +
                    "VALUES (" + i + ", 'Name_" + i + "', " + (i * 10.5) + ", 'Description for record " + i + "')"
            );
        }

        // Test pagination with LIMIT and OFFSET
        int pageSize = 100;
        int totalPages = totalRecords / pageSize;
        
        for (int page = 0; page < totalPages; page++) {
            int offset = page * pageSize;
            java.sql.PreparedStatement psSelect = conn.prepareStatement(
                "SELECT * FROM cockroachdb_pagination_test ORDER BY id LIMIT " + pageSize + " OFFSET " + offset
            );
            ResultSet resultSet = psSelect.executeQuery();
            
            int count = 0;
            while (resultSet.next()) {
                int expectedId = offset + count + 1;
                Assert.assertEquals(expectedId, resultSet.getInt("id"));
                Assert.assertEquals("Name_" + expectedId, resultSet.getString("name"));
                count++;
            }
            
            Assert.assertEquals(pageSize, count);
            resultSet.close();
            psSelect.close();
        }

        executeUpdate(conn, "DROP TABLE cockroachdb_pagination_test");
        if (xaConn != null) xaConn.close();
        conn.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCockroachDBLargeResultSetWithVariousTypes(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException, ClassNotFoundException {
        assumeFalse(isTestDisabled, "Skipping CockroachDB tests");
        
        Connection conn;
        XAConnection xaConn = null;
        if (isXA) {
            OjpXADataSource xaDataSource = new OjpXADataSource();
            xaDataSource.setUrl(url);
            xaDataSource.setUser(user);
            xaDataSource.setPassword(pwd);
            xaConn = xaDataSource.getXAConnection(user, pwd);
            conn = xaConn.getConnection();
        } else {
            conn = DriverManager.getConnection(url, user, pwd);
        }

        System.out.println("Testing CockroachDB large result set with various types for url -> " + url);

        try {
            executeUpdate(conn, "DROP TABLE cockroachdb_large_types_test");
        } catch (Exception e) {
            //Does not matter
        }
        
        // Create table with various CockroachDB data types
        executeUpdate(conn, "CREATE TABLE cockroachdb_large_types_test(" +
                "id SERIAL PRIMARY KEY, " +
                "int_val INT, " +
                "bigint_val BIGINT, " +
                "float_val FLOAT, " +
                "decimal_val DECIMAL(15,3), " +
                "text_val TEXT, " +
                "bool_val BOOLEAN, " +
                "timestamp_val TIMESTAMP)");

        // Insert 2000 records
        int totalRecords = 2000;
        for (int i = 1; i <= totalRecords; i++) {
            executeUpdate(conn,
                    "INSERT INTO cockroachdb_large_types_test " +
                    "(int_val, bigint_val, float_val, decimal_val, text_val, bool_val, timestamp_val) " +
                    "VALUES (" + i + ", " + (i * 1000L) + ", " + (i * 1.5) + ", " + (i * 100.123) + ", " +
                    "'Text value for record " + i + "', " + (i % 2 == 0) + ", CURRENT_TIMESTAMP)"
            );
        }

        // Retrieve all records and verify
        java.sql.PreparedStatement psSelect = conn.prepareStatement(
            "SELECT * FROM cockroachdb_large_types_test ORDER BY id"
        );
        ResultSet resultSet = psSelect.executeQuery();

        int count = 0;
        while (resultSet.next()) {
            count++;
            // Verify some fields
            Assert.assertEquals(count, resultSet.getInt("int_val"));
            Assert.assertEquals(count * 1000L, resultSet.getLong("bigint_val"));
            Assert.assertEquals("Text value for record " + count, resultSet.getString("text_val"));
            Assert.assertEquals(count % 2 == 0, resultSet.getBoolean("bool_val"));
        }

        Assert.assertEquals(totalRecords, count);

        resultSet.close();
        psSelect.close();
        executeUpdate(conn, "DROP TABLE cockroachdb_large_types_test");
        if (xaConn != null) xaConn.close();
        conn.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cockroachdb_connection.csv")
    public void testCockroachDBFetchSizePerformance(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException, ClassNotFoundException {
        assumeFalse(isTestDisabled, "Skipping CockroachDB tests");
        
        Connection conn;
        XAConnection xaConn = null;
        if (isXA) {
            OjpXADataSource xaDataSource = new OjpXADataSource();
            xaDataSource.setUrl(url);
            xaDataSource.setUser(user);
            xaDataSource.setPassword(pwd);
            xaConn = xaDataSource.getXAConnection(user, pwd);
            conn = xaConn.getConnection();
        } else {
            conn = DriverManager.getConnection(url, user, pwd);
        }

        System.out.println("Testing CockroachDB fetch size performance for url -> " + url);

        try {
            executeUpdate(conn, "DROP TABLE cockroachdb_fetch_size_test");
        } catch (Exception e) {
            //Does not matter
        }
        
        executeUpdate(conn, "CREATE TABLE cockroachdb_fetch_size_test(" +
                "id SERIAL PRIMARY KEY, " +
                "data VARCHAR(255))");

        // Insert 3000 records
        int totalRecords = 3000;
        for (int i = 1; i <= totalRecords; i++) {
            executeUpdate(conn,
                    "INSERT INTO cockroachdb_fetch_size_test (data) VALUES ('Data row " + i + "')"
            );
        }

        // Test with different fetch sizes
        int[] fetchSizes = {10, 50, 100, 500};
        
        for (int fetchSize : fetchSizes) {
            java.sql.PreparedStatement psSelect = conn.prepareStatement(
                "SELECT * FROM cockroachdb_fetch_size_test ORDER BY id"
            );
            psSelect.setFetchSize(fetchSize);
            
            ResultSet resultSet = psSelect.executeQuery();
            
            int count = 0;
            while (resultSet.next()) {
                count++;
            }
            
            Assert.assertEquals(totalRecords, count);
            
            resultSet.close();
            psSelect.close();
        }

        executeUpdate(conn, "DROP TABLE cockroachdb_fetch_size_test");
        if (xaConn != null) xaConn.close();
        conn.close();
    }
}
