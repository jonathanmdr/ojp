package openjdbcproxy.jdbc;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static openjdbcproxy.helpers.SqlHelper.executeUpdate;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class BlobIntegrationTest {

    private static boolean isMySQLTestDisabled;
    private static boolean isMariaDBTestDisabled;
    private static boolean isOracleTestEnabled;
    private String tableName;
    private Connection conn;

    @BeforeAll
    public static void checkTestConfiguration() {
        isMySQLTestDisabled = Boolean.parseBoolean(System.getProperty("disableMySQLTests", "false"));
        isMariaDBTestDisabled = Boolean.parseBoolean(System.getProperty("disableMariaDBTests", "false"));
        isOracleTestEnabled = Boolean.parseBoolean(System.getProperty("enableOracleTests", "false"));
    }

    public void setUp(String driverClass, String url, String user, String pwd) throws SQLException, ClassNotFoundException {
        assumeFalse(isMySQLTestDisabled, "MySQL tests are disabled");
        assumeFalse(isMariaDBTestDisabled, "MariaDB tests are disabled");
        assumeFalse(!isOracleTestEnabled, "Oracle tests are disabled");
        this.tableName = "blob_test_blob";
        if (url.toLowerCase().contains("mysql")) {
            this.tableName += "_mysql";
        } else if (url.toLowerCase().contains("mariadb")) {
            this.tableName += "_mariadb";
        } else if (url.toLowerCase().contains("oracle")) {
            this.tableName += "_oracle";
        } else {
            this.tableName += "_h2";
        }
        Class.forName(driverClass);
        this.conn = DriverManager.getConnection(url, user, pwd);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/h2_mysql_mariadb_oracle_connections.csv")
    public void createAndReadingBLOBsSuccessful(String driverClass, String url, String user, String pwd) throws SQLException, ClassNotFoundException, IOException {
        this.setUp(driverClass, url, user, pwd);
        System.out.println("Testing for url -> " + url);

        try {
            executeUpdate(conn, "drop table " + tableName);
        } catch (Exception e) {
            //If fails disregard as per the table is most possibly not created yet
        }

        executeUpdate(conn,
                "create table " + tableName + "(" +
                        " val_blob  BLOB," +
                        " val_blob2 BLOB," +
                        " val_blob3 BLOB" +
                        ")"
        );

        PreparedStatement psInsert = conn.prepareStatement(
                " insert into " + tableName + " (val_blob, val_blob2, val_blob3) values (?, ?, ?)"
        );

        String testString = "TEST STRING BLOB";
        Blob blob = conn.createBlob(); //WHEN this happens a connection in the server is set to a session and I need to replicate that in the
        //prepared statement created previously
        blob.setBytes(1, testString.getBytes());
        psInsert.setBlob(1, blob);
        String testString2 = "BLOB VIA INPUT STREAM";
        InputStream inputStream = new ByteArrayInputStream(testString2.getBytes());
        psInsert.setBlob(2 , inputStream);
        InputStream inputStream2 = new ByteArrayInputStream(testString2.getBytes());
        psInsert.setBlob(3, inputStream2, 5);
        psInsert.executeUpdate();

        java.sql.PreparedStatement psSelect = conn.prepareStatement("select val_blob, val_blob2, val_blob3 from " + tableName);
        ResultSet resultSet = psSelect.executeQuery();
        resultSet.next();
        Blob blobResult =  resultSet.getBlob(1);
        String fromBlobByIdx = new String(blobResult.getBinaryStream().readAllBytes());

        Assert.assertEquals(testString, fromBlobByIdx);

        Blob blobResultByName =  resultSet.getBlob("val_blob");
        String fromBlobByName = new String(blobResultByName.getBinaryStream().readAllBytes());
        Assert.assertEquals(testString, fromBlobByName);

        Blob blobResult2 =  resultSet.getBlob(2);
        String fromBlobByIdx2 = new String(blobResult2.getBinaryStream().readAllBytes());
        Assert.assertEquals(testString2, fromBlobByIdx2);

        Blob blobResult3 =  resultSet.getBlob(3);
        String fromBlobByIdx3 = new String(blobResult3.getBinaryStream().readAllBytes());
        Assert.assertEquals(testString2.substring(0, 5), fromBlobByIdx3);

        executeUpdate(conn, "delete from " + tableName);

        resultSet.close();
        psSelect.close();
        conn.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/h2_mysql_mariadb_connections.csv")
    public void creatingAndReadingLargeBLOBsSuccessful(String driverClass, String url, String user, String pwd) throws SQLException, IOException, ClassNotFoundException {
        this.setUp(driverClass, url, user, pwd);
        System.out.println("Testing for url -> " + url);

        try {
            executeUpdate(conn, "drop table " + tableName);
        } catch (Exception e) {
            //If fails disregard as per the table is most possibly not created yet
        }

        executeUpdate(conn,
                "create table " + tableName + "(" +
                        " val_blob  BLOB" +
                        ")"
        );

        PreparedStatement psInsert = conn.prepareStatement(
                "insert into " + tableName + " (val_blob) values (?)"
        );


        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("largeTextFile.txt");
        psInsert.setBlob(1 , inputStream);

        psInsert.executeUpdate();

        java.sql.PreparedStatement psSelect = conn.prepareStatement("select val_blob from " + tableName);
        ResultSet resultSet = psSelect.executeQuery();
        resultSet.next();
        Blob blobResult =  resultSet.getBlob(1);

        InputStream inputStreamTestFile = this.getClass().getClassLoader().getResourceAsStream("largeTextFile.txt");
        InputStream inputStreamBlob = blobResult.getBinaryStream();

        int byteFile = inputStreamTestFile.read();
        int count = 0;
        while (byteFile != -1) {
            count++;
            if (count == 3072) {
                System.out.println(count);
            }
            int blobByte = inputStreamBlob.read();
            //TODO remove after debugging
            if (byteFile != blobByte) {
                System.out.println(count);
            }

            Assert.assertEquals(byteFile, blobByte);
            byteFile = inputStreamTestFile.read();
        }

        executeUpdate(conn, "delete from " + tableName);

        resultSet.close();
        psSelect.close();
        conn.close();
    }

}
