package openjproxy.jdbc;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.openjproxy.jdbc.xa.OjpXADataSource;
import javax.sql.XAConnection;
import openjproxy.jdbc.testutil.TestDBUtils;
import openjproxy.jdbc.testutil.TestDBUtils.ConnectionResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static openjproxy.helpers.SqlHelper.executeUpdate;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * DB2-specific binary stream integration tests.
 * Tests DB2-specific binary data types (VARBINARY, BLOB) and stream handling.
 */
public class Db2BinaryStreamIntegrationTest {

    private static boolean isTestDisabled;

    @BeforeAll
    public static void setup() {
        isTestDisabled = !Boolean.parseBoolean(System.getProperty("enableDb2Tests", "false"));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/db2_connection.csv")
    public void createAndReadingBinaryStreamSuccessful(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException, ClassNotFoundException, IOException {
        assumeFalse(isTestDisabled, "Skipping DB2 tests");

        ConnectionResult connResult = TestDBUtils.createConnection(url, user, pwd, isXA);
        Connection conn = connResult.getConnection();

        // Set schema explicitly to avoid "object not found" errors
        try (Statement schemaStmt = conn.createStatement()) {
            schemaStmt.execute("SET SCHEMA DB2INST1");
        }

        System.out.println("Testing DB2 binary stream for url -> " + url);

        try {
            executeUpdate(conn, "drop table DB2INST1.db2_binary_stream_test");
        } catch (Exception e) {
            //If fails disregard as per the table is most possibly not created yet
        }

        // Create table with DB2-specific binary types
        executeUpdate(conn, "create table DB2INST1.db2_binary_stream_test(" +
                " val_varbinary1 VARBINARY(2000)," +  // DB2 VARBINARY for binary data
                " val_varbinary2 VARBINARY(2000)" +
                ")");

        conn.setAutoCommit(false);

        PreparedStatement psInsert = conn.prepareStatement(
                "insert into DB2INST1.db2_binary_stream_test (val_varbinary1, val_varbinary2) values (?, ?)"
        );

        String testString = "DB2 VARBINARY VIA INPUT STREAM";
        InputStream inputStream = new ByteArrayInputStream(testString.getBytes());
        psInsert.setBinaryStream(1, inputStream);

        InputStream inputStream2 = new ByteArrayInputStream(testString.getBytes());
        psInsert.setBinaryStream(2, inputStream2, 7);
        psInsert.executeUpdate();

        connResult.commit();

        PreparedStatement psSelect = conn.prepareStatement("select val_varbinary1, val_varbinary2 from DB2INST1.db2_binary_stream_test ");
        ResultSet resultSet = psSelect.executeQuery();
        resultSet.next();
        
        InputStream blobResult = resultSet.getBinaryStream(1);
        String fromBlobByIdx = new String(blobResult.readAllBytes());
        Assert.assertEquals(testString, fromBlobByIdx);

        InputStream blobResultByName = resultSet.getBinaryStream("val_varbinary1");
        byte[] allBytes = blobResultByName.readAllBytes();
        String fromBlobByName = new String(allBytes);
        Assert.assertEquals(testString, fromBlobByName);

        InputStream blobResult2 = resultSet.getBinaryStream(2);
        String fromBlobByIdx2 = new String(blobResult2.readAllBytes());
        Assert.assertEquals(testString.substring(0, 7), fromBlobByIdx2);

        executeUpdate(conn, "delete from db2_binary_stream_test");

        resultSet.close();
        psSelect.close();
        connResult.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/db2_connection.csv")
    public void createAndReadingLargeBinaryStreamSuccessful(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException, ClassNotFoundException, IOException {
        assumeFalse(isTestDisabled, "Skipping DB2 tests");

        ConnectionResult connResult = TestDBUtils.createConnection(url, user, pwd, isXA);
        Connection conn = connResult.getConnection();

        System.out.println("Testing DB2 large binary stream for url -> " + url);

        try {
            executeUpdate(conn, "drop table db2_large_binary_test");
        } catch (Exception e) {
            //If fails disregard as per the table is most possibly not created yet
        }

        // Create table with DB2 BLOB for large binary data
        executeUpdate(conn, "create table db2_large_binary_test(" +
                " val_blob BLOB(1M)" +
                ")");

        PreparedStatement psInsert = conn.prepareStatement(
                "insert into db2_large_binary_test (val_blob) values (?)"
        );

        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("largeTextFile.txt");
        psInsert.setBinaryStream(1, inputStream);

        psInsert.executeUpdate();

        PreparedStatement psSelect = conn.prepareStatement("select val_blob from db2_large_binary_test ");
        ResultSet resultSet = psSelect.executeQuery();
        resultSet.next();
        InputStream inputStreamBlob = resultSet.getBinaryStream(1);

        InputStream inputStreamTestFile = this.getClass().getClassLoader().getResourceAsStream("largeTextFile.txt");

        int byteFile = inputStreamTestFile.read();
        while (byteFile != -1) {
            int blobByte = inputStreamBlob.read();
            Assert.assertEquals(byteFile, blobByte);
            byteFile = inputStreamTestFile.read();
        }

        executeUpdate(conn, "delete from db2_large_binary_test");

        resultSet.close();
        psSelect.close();
        connResult.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/db2_connection.csv")
    public void testDb2SpecificBinaryHandling(String driverClass, String url, String user, String pwd, boolean isXA) throws SQLException, ClassNotFoundException, IOException {
        assumeFalse(isTestDisabled, "Skipping DB2 tests");

        ConnectionResult connResult = TestDBUtils.createConnection(url, user, pwd, isXA);
        Connection conn = connResult.getConnection();

        System.out.println("Testing DB2-specific binary handling for url -> " + url);

        try {
            executeUpdate(conn, "drop table db2_binary_types_test");
        } catch (Exception e) {
            //If fails disregard as per the table is most possibly not created yet
        }

        // Test different DB2 binary types
        executeUpdate(conn, "create table db2_binary_types_test(" +
                " small_varbinary VARBINARY(100)," +
                " medium_varbinary VARBINARY(2000)," +
                " large_blob BLOB(1M)" +
                ")");

        PreparedStatement psInsert = conn.prepareStatement(
                "insert into db2_binary_types_test (small_varbinary, medium_varbinary, large_blob) values (?, ?, ?)"
        );

        // Test different sizes
        String smallData = "Small VARBINARY data";
        String mediumData = "M".repeat(1000); // 1000 characters
        String largeData = "L".repeat(10000); // 10000 characters

        psInsert.setBinaryStream(1, new ByteArrayInputStream(smallData.getBytes()));
        psInsert.setBinaryStream(2, new ByteArrayInputStream(mediumData.getBytes()));
        psInsert.setBinaryStream(3, new ByteArrayInputStream(largeData.getBytes()));

        psInsert.executeUpdate();

        PreparedStatement psSelect = conn.prepareStatement("select small_varbinary, medium_varbinary, large_blob from db2_binary_types_test");
        ResultSet resultSet = psSelect.executeQuery();
        resultSet.next();

        // Verify small VARBINARY
        String retrievedSmall = new String(resultSet.getBinaryStream(1).readAllBytes());
        Assert.assertEquals(smallData, retrievedSmall);

        // Verify medium VARBINARY
        String retrievedMedium = new String(resultSet.getBinaryStream(2).readAllBytes());
        Assert.assertEquals(mediumData, retrievedMedium);

        // Verify large BLOB
        String retrievedLarge = new String(resultSet.getBinaryStream(3).readAllBytes());
        Assert.assertEquals(largeData, retrievedLarge);

        executeUpdate(conn, "delete from db2_binary_types_test");

        resultSet.close();
        psSelect.close();
        connResult.close();
    }
}
