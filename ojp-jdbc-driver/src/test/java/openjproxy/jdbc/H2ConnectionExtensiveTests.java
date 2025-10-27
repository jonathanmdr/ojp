package openjproxy.jdbc;

import io.grpc.StatusRuntimeException;
import lombok.SneakyThrows;
import openjproxy.jdbc.testutil.TestDBUtils;
import org.junit.jupiter.api.AfterEach;
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

public class H2ConnectionExtensiveTests {

    private Connection connection;

    @SneakyThrows
    public void setUp(String driverClass, String url, String user, String password) throws SQLException {
        connection = DriverManager.getConnection(url, user, password);
    }

    @AfterEach
    public void tearDown() throws SQLException {
        TestDBUtils.closeQuietly(connection);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/h2_connection.csv")
    public void testConnectionProperties(String driverClass, String url, String user, String password) throws SQLException {
        this.setUp(driverClass, url, user, password);
        assertEquals(false, connection.isClosed());
        assertEquals(true, connection.isValid(5));
        assertEquals("PUBLIC", connection.getSchema());
        assertNull(connection.getClientInfo("nonexistent"));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/h2_connection.csv")
    public void testAutoCommitAndTransactionIsolation(String driverClass, String url, String user, String password) throws SQLException {
        this.setUp(driverClass, url, user, password);
        assertEquals(true, connection.getAutoCommit());
        connection.setAutoCommit(false);
        assertEquals(false, connection.getAutoCommit());

        int isolation = connection.getTransactionIsolation();
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, isolation);

        connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        assertEquals(Connection.TRANSACTION_SERIALIZABLE, connection.getTransactionIsolation());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/h2_connection.csv")
    public void testCommitAndRollback(String driverClass, String url, String user, String password) throws SQLException {
        this.setUp(driverClass, url, user, password);
        connection.setAutoCommit(false);

        TestDBUtils.createBasicTestTable(connection, "h2_connection_test", TestDBUtils.SqlSyntax.H2, true);
        connection.createStatement().execute("INSERT INTO h2_connection_test (id, name) VALUES (3, 'Charlie')");
        connection.rollback();

        ResultSet rs = connection.createStatement().executeQuery("SELECT * FROM h2_connection_test WHERE id = 3");
        assertEquals(false, rs.next());

        connection.createStatement().execute("INSERT INTO h2_connection_test (id, name) VALUES (3, 'Charlie')");
        connection.commit();

        rs = connection.createStatement().executeQuery("SELECT * FROM h2_connection_test");
        assertEquals(true, rs.next());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/h2_connection.csv")
    public void testSavepoints(String driverClass, String url, String user, String password) throws SQLException {
        this.setUp(driverClass, url, user, password);
        connection.setAutoCommit(false);
        TestDBUtils.createBasicTestTable(connection, "h2_connection_test", TestDBUtils.SqlSyntax.H2, true);

        Savepoint sp1 = connection.setSavepoint("Savepoint1");
        assertEquals("Savepoint1", sp1.getSavepointName());
        connection.createStatement().execute("INSERT INTO h2_connection_test (id, name) VALUES (3, 'Charlie')");
        connection.rollback(sp1);

        ResultSet rs = connection.createStatement().executeQuery("SELECT * FROM h2_connection_test WHERE id = 3");
        assertEquals(false, rs.next());

        connection.createStatement().execute("INSERT INTO h2_connection_test (id, name) VALUES (3, 'Charlie')");
        connection.releaseSavepoint(sp1);
        connection.commit();

        rs = connection.createStatement().executeQuery("SELECT * FROM h2_connection_test WHERE id = 3");
        assertEquals(true, rs.next());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/h2_connection.csv")
    public void testConnectionMetadata(String driverClass, String url, String user, String password) throws SQLException {
        this.setUp(driverClass, url, user, password);
        DatabaseMetaData metaData = connection.getMetaData();
        assertNotNull(metaData);
        assertEquals("H2", metaData.getDatabaseProductName());
        assertEquals(true, metaData.supportsTransactions());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/h2_connection.csv")
    public void testClientInfo(String driverClass, String url, String user, String password) throws SQLException {
        if (url.contains("h2")) {//Not supported in H2
            return;
        }
        this.setUp(driverClass, url, user, password);
        connection.setClientInfo("ApplicationName", "TestApp");
        assertEquals("TestApp", connection.getClientInfo("ApplicationName"));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/h2_connection.csv")
    public void testClose(String driverClass, String url, String user, String password) throws SQLException {
        this.setUp(driverClass, url, user, password);
        assertEquals(false, connection.isClosed());
        connection.close();
        assertEquals(true, connection.isClosed());
    }

    // ---------- Additional tests for every Connection interface method ----------

    @ParameterizedTest
    @CsvFileSource(resources = "/h2_connection.csv")
    public void testAllConnectionMethods(String driverClass, String url, String user, String password) throws Exception {
        this.setUp(driverClass, url, user, password);

        // createStatement
        Statement st1 = connection.createStatement();
        assertNotNull(st1);

        // prepareStatement(String)
        PreparedStatement ps1 = connection.prepareStatement("SELECT 1");
        assertNotNull(ps1);

        // prepareCall(String)
        CallableStatement cs1 = connection.prepareCall("CALL 1");
        assertNotNull(cs1);

        // nativeSQL
        assertEquals("SELECT 1", connection.nativeSQL("SELECT 1"));

        // setAutoCommit/getAutoCommit done elsewhere

        // commit/rollback/close done elsewhere

        // isClosed done elsewhere

        // getMetaData done elsewhere

        // setReadOnly / isReadOnly
        connection.setReadOnly(false);
        assertEquals(false, connection.isReadOnly());
        connection.setReadOnly(true);
        assertEquals(false, connection.isReadOnly());

        // setCatalog / getCatalog
        String oldCatalog = connection.getCatalog();
        connection.setCatalog(oldCatalog); // Should not throw
        assertEquals(oldCatalog, connection.getCatalog());

        // setTransactionIsolation / getTransactionIsolation done elsewhere

        // getWarnings / clearWarnings
        SQLWarning warning = connection.getWarnings();
        assertNull(warning); // H2: no warnings by default
        connection.clearWarnings();

        // createStatement(int, int)
        Statement st2 = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        assertNotNull(st2);

        // prepareStatement(String, int, int)
        PreparedStatement ps2 = connection.prepareStatement("SELECT 1", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        assertNotNull(ps2);

        // prepareCall(String, int, int)
        CallableStatement cs2 = connection.prepareCall("CALL 1", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        assertNotNull(cs2);

        // getTypeMap / setTypeMap
        Map<String, Class<?>> oldMap = connection.getTypeMap();
        assertNotNull(oldMap);
        connection.setTypeMap(oldMap);

        // setHoldability/getHoldability
        int hold = connection.getHoldability();
        connection.setHoldability(hold);
        assertEquals(hold, connection.getHoldability());

        // Set auto-commit to false before using savepoints!
        connection.setAutoCommit(false);

        // setSavepoint, setSavepoint(String)
        Savepoint sp1 = connection.setSavepoint();
        assertNotNull(sp1);
        Savepoint sp2 = connection.setSavepoint("sp2");
        assertNotNull(sp2);

        // rollback(Savepoint), releaseSavepoint(Savepoint)
        connection.rollback(sp1);
        connection.releaseSavepoint(sp2);

        // createStatement(int, int, int)
        Statement st3 = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT);
        assertNotNull(st3);

        // prepareStatement(String, int, int, int)
        PreparedStatement ps3 = connection.prepareStatement("SELECT 1", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT);
        assertNotNull(ps3);

        // prepareCall(String, int, int, int)
        CallableStatement cs3 = connection.prepareCall("CALL 1", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT);
        assertNotNull(cs3);

        // prepareStatement(String, int)
        PreparedStatement ps4 = connection.prepareStatement("SELECT 1", Statement.RETURN_GENERATED_KEYS);
        assertNotNull(ps4);

        // prepareStatement(String, int[])
        PreparedStatement ps5 = connection.prepareStatement("SELECT 1", new int[0]);
        assertNotNull(ps5);

        // prepareStatement(String, String[])
        PreparedStatement ps6 = connection.prepareStatement("SELECT 1", new String[0]);
        assertNotNull(ps6);

        // createClob, createBlob, createNClob, createSQLXML
        Clob clob = connection.createClob();
        assertNotNull(clob);
        Blob blob = connection.createBlob();
        assertNotNull(blob);
        NClob nclob = connection.createNClob();
        assertNotNull(nclob);
        SQLXML sqlxml = connection.createSQLXML();
        assertNotNull(sqlxml);

        // isValid
        assertEquals(true, connection.isValid(5));

        // setClientInfo (Properties)
        Properties props = new Properties();
        props.setProperty("foo", "bar");
        try {
            connection.setClientInfo(props);
        } catch (SQLException | StatusRuntimeException ignored) {}

        // setClientInfo(String, String)
        try {
            connection.setClientInfo("foo", "bar");
        } catch (SQLException | StatusRuntimeException ignored) {}

        // getClientInfo(String)
        String val = connection.getClientInfo("foo");
        assertTrue(val == null || val.equals("bar"));

        // getClientInfo()
        Properties p2 = connection.getClientInfo();
        assertNotNull(p2);

        // createArrayOf
        Array arr = connection.createArrayOf("INTEGER", new Object[]{1, 2, 3});
        assertNotNull(arr);

        // createStruct - H2 does not support Struct, so assertThrows!
        assertThrows(SQLFeatureNotSupportedException.class, () -> {
            connection.createStruct("YOUR_STRUCT_TYPE", new Object[]{});
        });

        // setSchema/getSchema
        String schema = connection.getSchema();
        connection.setSchema(schema);
        assertEquals(schema, connection.getSchema());

        // abort (should not throw)
        try {
            connection.abort(new Executor() {
                public void execute(Runnable command) {
                    new Thread(command).start();
                }
            });
        } catch (SQLFeatureNotSupportedException e) {
            //OJP does not support executors
        }

        // setNetworkTimeout/getNetworkTimeout
        connection.setNetworkTimeout(null, 1000);
        assertEquals(1000, connection.getNetworkTimeout());

        // beginRequest/endRequest (defaults, should not throw)
        connection.beginRequest();
        connection.endRequest();

        // setShardingKeyIfValid/setShardingKey (should throw SQLFeatureNotSupportedException)
        assertThrows(SQLFeatureNotSupportedException.class, () -> {
            connection.setShardingKeyIfValid(null, null, 0);
        });
        assertThrows(SQLFeatureNotSupportedException.class, () -> {
            connection.setShardingKeyIfValid(null, 0);
        });
        assertThrows(SQLFeatureNotSupportedException.class, () -> {
            connection.setShardingKey(null, null);
        });
        assertThrows(SQLFeatureNotSupportedException.class, () -> {
            connection.setShardingKey(null);
        });
    }
}