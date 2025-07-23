package openjdbcproxy.jdbc;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import static openjdbcproxy.helpers.SqlHelper.executeUpdate;

@Slf4j
public class BasicCrudIntegrationTest {

    private static boolean isPostgresTestDisabled;
    private static boolean isMySQLTestDisabled;
    private static boolean isMariaDBTestDisabled;
    private static boolean isOracleTestDisabled;

    @BeforeAll
    public static void setup() {
        isPostgresTestDisabled = Boolean.parseBoolean(System.getProperty("disablePostgresTests", "false"));
        isMySQLTestDisabled = Boolean.parseBoolean(System.getProperty("disableMySQLTests", "false"));
        isMariaDBTestDisabled = Boolean.parseBoolean(System.getProperty("disableMariaDBTests", "false"));
        isOracleTestDisabled = Boolean.parseBoolean(System.getProperty("disableOracleTests", "false"));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/h2_postgres_mysql_mariadb_connections.csv")
    public void crudTestSuccessful(String driverClass, String url, String user, String pwd) throws SQLException, ClassNotFoundException {
        // Skip PostgreSQL tests if disabled
        if (url.toLowerCase().contains("postgresql") && isPostgresTestDisabled) {
            Assumptions.assumeFalse(true, "Skipping Postgres tests");
        }
        
        // Skip MySQL tests if disabled
        if (url.toLowerCase().contains("mysql") && isMySQLTestDisabled) {
            Assumptions.assumeFalse(true, "Skipping MySQL tests");
        }

        // Skip MariaDB tests if disabled
        if (url.toLowerCase().contains("mariadb") && isMariaDBTestDisabled) {
            Assumptions.assumeFalse(true, "Skipping MariaDB tests");
        }

        // Skip Oracle tests if disabled
        if (url.toLowerCase().contains("oracle") && isOracleTestDisabled) {
            Assumptions.assumeFalse(true, "Skipping Oracle tests");
        }

        Connection conn = DriverManager.getConnection(url, user, pwd);

        System.out.println("Testing for url -> " + url);

        try {
            executeUpdate(conn, "drop table basic_crud_test");
        } catch (Exception e) {
            //Does not matter
        }

        executeUpdate(conn, "create table basic_crud_test(" +
                "id INT NOT NULL," +
                "title VARCHAR(50) NOT NULL" +
                ")");

        executeUpdate(conn, " insert into basic_crud_test (id, title) values (1, 'TITLE_1')");

        java.sql.PreparedStatement psSelect = conn.prepareStatement("select * from basic_crud_test where id = ?");
        psSelect.setInt(1, 1);
        ResultSet resultSet = psSelect.executeQuery();
        resultSet.next();
        int id = resultSet.getInt(1);
        String title = resultSet.getString(2);
        Assert.assertEquals(1, id);
        Assert.assertEquals("TITLE_1", title);

        executeUpdate(conn, "update basic_crud_test set title='TITLE_1_UPDATED'");

        ResultSet resultSetUpdated = psSelect.executeQuery();
        resultSetUpdated.next();
        int idUpdated = resultSetUpdated.getInt(1);
        String titleUpdated = resultSetUpdated.getString(2);
        Assert.assertEquals(1, idUpdated);
        Assert.assertEquals("TITLE_1_UPDATED", titleUpdated);

        executeUpdate(conn, " delete from basic_crud_test where id=1 and title='TITLE_1_UPDATED'");

        ResultSet resultSetAfterDeletion = psSelect.executeQuery();
        Assert.assertFalse(resultSetAfterDeletion.next());

        resultSet.close();
        psSelect.close();
        conn.close();
    }

}
