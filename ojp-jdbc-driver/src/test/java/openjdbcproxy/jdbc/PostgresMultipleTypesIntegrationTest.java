package openjdbcproxy.jdbc;

import openjdbcproxy.jdbc.testutil.TestDBUtils;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class PostgresMultipleTypesIntegrationTest {

    private static boolean isTestDisabled;

    @BeforeAll
    public static void checkTestConfiguration() {
        isTestDisabled = Boolean.parseBoolean(System.getProperty("disablePostgresTests", "false"));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/postgres_connection.csv")
    public void typesCoverageTestSuccessful(String driverClass, String url, String user, String pwd) throws SQLException, ClassNotFoundException, ParseException {
        assumeFalse(isTestDisabled, "Postgres tests are disabled");
        assumeTrue(TestDBUtils.isInfrastructureAvailable(driverClass, url, user, pwd), 
                   "OJP server or PostgreSQL infrastructure not available - skipping integration test");
        
        Connection conn = DriverManager.getConnection(url, user, pwd);

        System.out.println("Testing for url -> " + url);

        TestDBUtils.createMultiTypeTestTable(conn, TestDBUtils.SqlSyntax.POSTGRES);

        java.sql.PreparedStatement psInsert = conn.prepareStatement(
                "insert into test_table (val_int, val_varchar, val_double_precision, val_bigint, val_tinyint, " +
                        "val_smallint, val_boolean, val_decimal, val_float, val_byte, val_binary, val_date, val_time, " +
                        "val_timestamp) " +
                        "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        );

        psInsert.setInt(1, 1);
        psInsert.setString(2, "TITLE_1");
        psInsert.setDouble(3, 2.2222d);
        psInsert.setLong(4, 33333333333333l);
        psInsert.setInt(5, 127); // PostgreSQL SMALLINT can handle this
        psInsert.setInt(6, 32767);
        psInsert.setBoolean(7, true);
        psInsert.setBigDecimal(8, new BigDecimal(10));
        psInsert.setFloat(9, 20.20f);
        psInsert.setByte(10, (byte) 1);
        psInsert.setBytes(11, "AAAA".getBytes()); // PostgreSQL BYTEA
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        psInsert.setDate(12, new Date(sdf.parse("29/03/2025").getTime()));
        SimpleDateFormat sdfTime = new SimpleDateFormat("hh:mm:ss");
        psInsert.setTime(13, new Time(sdfTime.parse("11:12:13").getTime()));
        SimpleDateFormat sdfTimestamp = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        psInsert.setTimestamp(14, new Timestamp(sdfTimestamp.parse("30/03/2025 21:22:23").getTime()));
        psInsert.executeUpdate();

        java.sql.PreparedStatement psSelect = conn.prepareStatement("select * from test_table where val_int = ?");
        psSelect.setInt(1, 1);
        ResultSet resultSet = psSelect.executeQuery();
        resultSet.next();
        Assert.assertEquals(1, resultSet.getInt(1));
        Assert.assertEquals("TITLE_1", resultSet.getString(2));
        Assert.assertEquals("2.2222", ""+resultSet.getDouble(3));
        Assert.assertEquals(33333333333333L, resultSet.getLong(4));
        Assert.assertEquals(127, resultSet.getInt(5)); // SMALLINT in PostgreSQL
        Assert.assertEquals(32767, resultSet.getInt(6));
        Assert.assertEquals(true, resultSet.getBoolean(7));
        Assert.assertEquals(new BigDecimal(10), resultSet.getBigDecimal(8));
        Assert.assertEquals(20.20f+"", ""+resultSet.getFloat(9));
        Assert.assertEquals((byte) 1, resultSet.getBytes(10)[0]); // BYTEA returns byte array
        Assert.assertEquals("AAAA", new String(resultSet.getBytes(11)));
        Assert.assertEquals("29/03/2025", sdf.format(resultSet.getDate(12)));
        Assert.assertEquals("11:12:13", sdfTime.format(resultSet.getTime(13)));
        Assert.assertEquals("30/03/2025 21:22:23", sdfTimestamp.format(resultSet.getTimestamp(14)));

        // Test column name access
        Assert.assertEquals(1, resultSet.getInt("val_int"));
        Assert.assertEquals("TITLE_1", resultSet.getString("val_varchar"));
        Assert.assertEquals("2.2222", ""+resultSet.getDouble("val_double_precision"));
        Assert.assertEquals(33333333333333L, resultSet.getLong("val_bigint"));
        Assert.assertEquals(127, resultSet.getInt("val_tinyint"));
        Assert.assertEquals(32767, resultSet.getInt("val_smallint"));
        Assert.assertEquals(new BigDecimal(10), resultSet.getBigDecimal("val_decimal"));
        Assert.assertEquals(20.20f+"", ""+resultSet.getFloat("val_float"));
        Assert.assertEquals(true, resultSet.getBoolean("val_boolean"));
        Assert.assertEquals((byte) 1, resultSet.getBytes("val_byte")[0]); // BYTEA returns byte array
        Assert.assertEquals("AAAA", new String(resultSet.getBytes("val_binary")));
        Assert.assertEquals("29/03/2025", sdf.format(resultSet.getDate("val_date")));
        Assert.assertEquals("11:12:13", sdfTime.format(resultSet.getTime("val_time")));
        Assert.assertEquals("30/03/2025 21:22:23", sdfTimestamp.format(resultSet.getTimestamp("val_timestamp")));

        TestDBUtils.executeUpdate(conn, "delete from test_table where val_int=1");

        ResultSet resultSetAfterDeletion = psSelect.executeQuery();
        Assert.assertFalse(resultSetAfterDeletion.next());

        resultSet.close();
        psSelect.close();
        conn.close();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/postgres_connection.csv")
    public void testPostgresSpecificTypes(String driverClass, String url, String user, String pwd) throws SQLException, ClassNotFoundException {
        assumeFalse(isTestDisabled, "Postgres tests are disabled");
        assumeTrue(TestDBUtils.isInfrastructureAvailable(driverClass, url, user, pwd), 
                   "OJP server or PostgreSQL infrastructure not available - skipping integration test");
        
        Connection conn = DriverManager.getConnection(url, user, pwd);

        System.out.println("Testing PostgreSQL-specific types for url -> " + url);

        // Test UUID, JSON, and array types (PostgreSQL-specific)
        try {
            TestDBUtils.executeUpdate(conn, "DROP TABLE test_postgres_types");
        } catch (Exception e) {
            // Ignore if table doesn't exist
        }

        TestDBUtils.executeUpdate(conn, 
            "CREATE TABLE test_postgres_types (" +
            "id SERIAL PRIMARY KEY, " +
            "uuid_col UUID, " +
            "json_col JSON, " +
            "array_col INTEGER[], " +
            "text_col TEXT)"
        );

        java.sql.PreparedStatement psInsert = conn.prepareStatement(
            "INSERT INTO test_postgres_types (uuid_col, json_col, array_col, text_col) VALUES (?, ?::json, ?, ?)"
        );

        // Test UUID
        psInsert.setObject(1, java.util.UUID.randomUUID());
        // Test JSON
        psInsert.setString(2, "{\"key\": \"value\"}");
        // Test Array
        java.sql.Array array = conn.createArrayOf("INTEGER", new Integer[]{1, 2, 3});
        psInsert.setArray(3, array);
        // Test TEXT
        psInsert.setString(4, "PostgreSQL text type");

        psInsert.executeUpdate();

        java.sql.PreparedStatement psSelect = conn.prepareStatement("SELECT * FROM test_postgres_types WHERE id = 1");
        ResultSet resultSet = psSelect.executeQuery();
        
        Assert.assertTrue(resultSet.next());
        Assert.assertNotNull(resultSet.getObject("uuid_col"));
        Assert.assertEquals("{\"key\": \"value\"}", resultSet.getString("json_col"));
        java.sql.Array resultArray = resultSet.getArray("array_col");
        Assert.assertNotNull(resultArray);
        Assert.assertEquals("PostgreSQL text type", resultSet.getString("text_col"));

        resultSet.close();
        psSelect.close();
        psInsert.close();
        conn.close();
    }
}