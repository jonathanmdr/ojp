package org.openjproxy.database;

import com.openjproxy.grpc.DbName;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@UtilityClass
public class DatabaseUtils {


    public DbName resolveDbName(String url) {
        DbName dbName = DbName.UNMAPPED;
        if (url.toUpperCase().contains("H2:")) {
            dbName = DbName.H2;
            log.debug("H2DB detected");
        } else if (url.toUpperCase().contains("MYSQL:")) {
            dbName = DbName.MYSQL;
            log.debug("MySql detected");
        } else if (url.toUpperCase().contains("MARIADB:")) {
            dbName = DbName.MARIADB;
            log.debug("MariaDB detected");
        } else if (url.toUpperCase().contains("POSTGRESQL:")) {
            dbName = DbName.POSTGRES;
            log.debug("PostgreSQL detected");
        } else if (url.toUpperCase().contains("ORACLE:")) {
            dbName = DbName.ORACLE;
            log.debug("Oracle detected");
        } else if (url.toUpperCase().contains("SQLSERVER:")) {
            dbName = DbName.SQL_SERVER;
            log.debug("SQL Server detected");
        } else if (url.toUpperCase().contains("DB2:")) {
            dbName = DbName.DB2;
            log.debug("DB2 detected");
        }

        return dbName;
    }
}
